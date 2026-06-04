package main

import (
	"bytes"
	"context"
	"encoding/base64"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"net/netip"
	"os"
	"runtime"
	"strings"
	"time"

	"tailscale.com/ipn/ipnstate"
	"tailscale.com/net/netmon"
	"tailscale.com/tsnet"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/knownhosts"
)

func main() {
	registerAndroidInterfaceGetter()

	stateDir := flag.String("state", "", "tailscale state directory")
	hostname := flag.String("hostname", "android-rsync", "tailscale node name")
	timeout := flag.Duration("timeout", 30*time.Second, "connect timeout")
	authKey := flag.String("authkey", "", "tailscale auth key; TS_AUTHKEY is also honored")
	upOnly := flag.Bool("up", false, "authenticate/start tailscale and print status")
	checkOnly := flag.Bool("check", false, "connect to host port, print success, then exit")
	listenAddr := flag.String("listen", "", "listen locally and forward each connection to host port")
	sshKeyscan := flag.Bool("ssh-keyscan", false, "scan the SSH host key over tsnet and print a known_hosts line")
	sshInstallAuthorizedKey := flag.Bool("ssh-install-authorized-key", false, "install an SSH public key using password auth over tsnet")
	sshUser := flag.String("user", "", "SSH username for --ssh-keyscan or --ssh-install-authorized-key")
	passwordFile := flag.String("password-file", "", "file containing the SSH password for --ssh-install-authorized-key")
	publicKeyFile := flag.String("public-key-file", "", "file containing the public key for --ssh-install-authorized-key")
	knownHostsFile := flag.String("known-hosts", "", "known_hosts file for --ssh-install-authorized-key")
	flag.Parse()

	if *stateDir == "" {
		fmt.Fprintln(os.Stderr, "tsnet state directory is required")
		os.Exit(2)
	}
	if *upOnly && *checkOnly {
		fmt.Fprintln(os.Stderr, "--up and --check are mutually exclusive")
		os.Exit(2)
	}
	selectedModes := 0
	for _, enabled := range []bool{
		*upOnly,
		*checkOnly,
		*listenAddr != "",
		*sshKeyscan,
		*sshInstallAuthorizedKey,
	} {
		if enabled {
			selectedModes++
		}
	}
	if selectedModes > 1 {
		fmt.Fprintln(os.Stderr, "--up, --check, --listen, --ssh-keyscan, and --ssh-install-authorized-key are mutually exclusive")
		os.Exit(2)
	}

	server := &tsnet.Server{
		Dir:      *stateDir,
		Hostname: *hostname,
		AuthKey:  *authKey,
	}
	defer server.Close()

	if *upOnly {
		ctx, cancel := context.WithTimeout(context.Background(), *timeout)
		defer cancel()
		if flag.NArg() != 0 {
			fmt.Fprintln(os.Stderr, "usage: tsnet-nc --state DIR --hostname NAME --up")
			os.Exit(2)
		}
		status, err := server.Up(ctx)
		if err != nil {
			fmt.Fprintf(os.Stderr, "tsnet up failed: %v\n", err)
			os.Exit(1)
		}
		fmt.Printf("state=%s ips=%s\n", status.BackendState, joinedIPs(status.TailscaleIPs))
		return
	}

	if flag.NArg() != 2 {
		fmt.Fprintln(os.Stderr, "usage: tsnet-nc [--state DIR] [--hostname NAME] [--check] host port")
		os.Exit(2)
	}

	host := flag.Arg(0)
	port := flag.Arg(1)
	upCtx, upCancel := context.WithTimeout(context.Background(), *timeout)
	status, err := server.Up(upCtx)
	if err != nil {
		upCancel()
		fmt.Fprintf(os.Stderr, "tsnet up failed: %v\n", err)
		os.Exit(1)
	}
	upCancel()
	printStatusDiagnostics(status, host)
	if message := missingTargetPeerMessage(status, host); message != "" {
		fmt.Fprintln(os.Stderr, message)
		os.Exit(1)
	}
	dialHost := resolveTargetHost(status, host)
	if dialHost != host {
		fmt.Fprintf(os.Stderr, "tsnet resolved target %q to %q\n", host, dialHost)
	}

	if *listenAddr != "" {
		if err := listenAndForward(server, *listenAddr, dialHost, port, *timeout); err != nil {
			fmt.Fprintf(os.Stderr, "tsnet listen failed: %v\n", err)
			os.Exit(1)
		}
		return
	}

	dialCtx, dialCancel := context.WithTimeout(context.Background(), *timeout)
	defer dialCancel()
	conn, err := server.Dial(dialCtx, "tcp", net.JoinHostPort(dialHost, port))
	if err != nil {
		if errors.Is(dialCtx.Err(), context.DeadlineExceeded) {
			fmt.Fprintln(os.Stderr, "tsnet connect timed out")
		} else {
			fmt.Fprintf(os.Stderr, "tsnet connect failed: %v\n", err)
		}
		os.Exit(1)
	}
	defer conn.Close()

	if *sshKeyscan {
		if *sshUser == "" {
			fmt.Fprintln(os.Stderr, "--user is required with --ssh-keyscan")
			os.Exit(2)
		}
		if err := scanSSHHostKey(conn, host, port, *sshUser, *timeout); err != nil {
			fmt.Fprintf(os.Stderr, "ssh host key scan failed: %v\n", err)
			os.Exit(1)
		}
		return
	}

	if *sshInstallAuthorizedKey {
		if *sshUser == "" || *passwordFile == "" || *publicKeyFile == "" || *knownHostsFile == "" {
			fmt.Fprintln(os.Stderr, "--user, --password-file, --public-key-file, and --known-hosts are required with --ssh-install-authorized-key")
			os.Exit(2)
		}
		if err := installAuthorizedKey(conn, host, port, *sshUser, *passwordFile, *publicKeyFile, *knownHostsFile, *timeout); err != nil {
			fmt.Fprintf(os.Stderr, "ssh install authorized key failed: %v\n", err)
			os.Exit(1)
		}
		return
	}

	if *checkOnly {
		fmt.Printf("connected %s\n", net.JoinHostPort(host, port))
		return
	}

	if err := bridgeConns(conn, os.Stdin, os.Stdout); err != nil {
		fmt.Fprintf(os.Stderr, "tsnet stream failed: %v\n", err)
		os.Exit(1)
	}
}

func listenAndForward(server *tsnet.Server, listenAddr, host, port string, timeout time.Duration) error {
	listener, err := net.Listen("tcp", listenAddr)
	if err != nil {
		return err
	}
	defer listener.Close()
	fmt.Printf("listening %s\n", listener.Addr().String())

	for {
		localConn, err := listener.Accept()
		if err != nil {
			return err
		}
		go func() {
			defer localConn.Close()
			ctx, cancel := context.WithTimeout(context.Background(), timeout)
			defer cancel()
			remoteConn, err := server.Dial(ctx, "tcp", net.JoinHostPort(host, port))
			if err != nil {
				fmt.Fprintf(os.Stderr, "tsnet forward dial failed: %v\n", err)
				return
			}
			defer remoteConn.Close()
			if err := bridgeConns(remoteConn, localConn, localConn); err != nil {
				fmt.Fprintf(os.Stderr, "tsnet forward stream failed: %v\n", err)
			}
		}()
	}
}

func bridgeConns(remote net.Conn, input io.Reader, output io.Writer) error {
	errc := make(chan error, 2)
	go func() {
		_, err := io.Copy(remote, input)
		if c, ok := remote.(interface{ CloseWrite() error }); ok {
			_ = c.CloseWrite()
		}
		errc <- err
	}()
	go func() {
		_, err := io.Copy(output, remote)
		errc <- err
	}()

	if err := <-errc; err != nil && !errors.Is(err, net.ErrClosed) {
		return err
	}
	return nil
}

func scanSSHHostKey(conn net.Conn, host, port, user string, timeout time.Duration) error {
	var captured ssh.PublicKey
	config := &ssh.ClientConfig{
		User:            user,
		Auth:            []ssh.AuthMethod{},
		HostKeyCallback: func(hostname string, remote net.Addr, key ssh.PublicKey) error { captured = key; return nil },
		Timeout:         timeout,
	}
	sshConn, chans, reqs, err := ssh.NewClientConn(conn, net.JoinHostPort(host, port), config)
	if sshConn != nil {
		sshConn.Close()
	}
	if reqs != nil {
		go ssh.DiscardRequests(reqs)
	}
	if chans != nil {
		go func() {
			for ch := range chans {
				_ = ch.Reject(ssh.UnknownChannelType, "host key scan only")
			}
		}()
	}
	if captured == nil {
		return err
	}
	fmt.Printf("%s %s %s\n", knownHostPattern(host, port), captured.Type(), base64.StdEncoding.EncodeToString(captured.Marshal()))
	return nil
}

func installAuthorizedKey(
	conn net.Conn,
	host string,
	port string,
	user string,
	passwordFile string,
	publicKeyFile string,
	knownHostsFile string,
	timeout time.Duration,
) error {
	passwordBytes, err := os.ReadFile(passwordFile)
	if err != nil {
		return err
	}
	publicKeyBytes, err := os.ReadFile(publicKeyFile)
	if err != nil {
		return err
	}
	hostKeyCallback, err := knownhosts.New(knownHostsFile)
	if err != nil {
		return err
	}
	password := strings.TrimRight(string(passwordBytes), "\r\n")
	config := &ssh.ClientConfig{
		User: user,
		Auth: []ssh.AuthMethod{
			ssh.Password(password),
			ssh.KeyboardInteractive(func(_ string, _ string, questions []string, _ []bool) ([]string, error) {
				answers := make([]string, len(questions))
				for index := range answers {
					answers[index] = password
				}
				return answers, nil
			}),
		},
		HostKeyCallback: hostKeyCallback,
		Timeout:         timeout,
	}
	sshConn, chans, reqs, err := ssh.NewClientConn(conn, net.JoinHostPort(host, port), config)
	if err != nil {
		return err
	}
	client := ssh.NewClient(sshConn, chans, reqs)
	defer client.Close()

	session, err := client.NewSession()
	if err != nil {
		return err
	}
	defer session.Close()

	var stdout bytes.Buffer
	var stderr bytes.Buffer
	session.Stdout = &stdout
	session.Stderr = &stderr
	err = session.Run(authorizedKeysInstallScript(strings.TrimSpace(string(publicKeyBytes))))
	if stdout.Len() > 0 {
		_, _ = io.Copy(os.Stdout, &stdout)
	}
	if stderr.Len() > 0 {
		_, _ = io.Copy(os.Stderr, &stderr)
	}
	return err
}

func authorizedKeysInstallScript(publicKey string) string {
	quotedKey := shellQuote(publicKey)
	return strings.Join([]string{
		"set -eu",
		"umask 077",
		"mkdir -p \"$HOME/.ssh\"",
		"touch \"$HOME/.ssh/authorized_keys\"",
		"if ! grep -qxF -- " + quotedKey + " \"$HOME/.ssh/authorized_keys\"; then",
		"  printf '%s\\n' " + quotedKey + " >> \"$HOME/.ssh/authorized_keys\"",
		"fi",
		"chmod 700 \"$HOME/.ssh\"",
		"chmod 600 \"$HOME/.ssh/authorized_keys\"",
	}, "\n")
}

func shellQuote(value string) string {
	if value == "" {
		return "''"
	}
	return "'" + strings.ReplaceAll(value, "'", "'\\''") + "'"
}

func knownHostPattern(hostname, port string) string {
	if port == "22" || strings.HasPrefix(hostname, "[") {
		return hostname
	}
	return "[" + hostname + "]:" + port
}

func joinedIPs(ips []netip.Addr) string {
	values := make([]string, 0, len(ips))
	for _, ip := range ips {
		values = append(values, ip.String())
	}
	return strings.Join(values, ",")
}

func printStatusDiagnostics(status *ipnstate.Status, targetHost string) {
	if status == nil {
		fmt.Fprintln(os.Stderr, "tsnet status unavailable")
		return
	}
	tailnetName := ""
	magicDNS := ""
	magicDNSEnabled := false
	if status.CurrentTailnet != nil {
		tailnetName = status.CurrentTailnet.Name
		magicDNS = status.CurrentTailnet.MagicDNSSuffix
		magicDNSEnabled = status.CurrentTailnet.MagicDNSEnabled
	}
	onlinePeers := 0
	for _, peer := range status.Peer {
		if peer.Online {
			onlinePeers++
		}
	}
	fmt.Fprintf(
		os.Stderr,
		"tsnet status state=%s selfIPs=%s peers=%d onlinePeers=%d tailnet=%q magicDNS=%q magicDNSEnabled=%t health=%s\n",
		status.BackendState,
		joinedIPs(status.TailscaleIPs),
		len(status.Peer),
		onlinePeers,
		tailnetName,
		magicDNS,
		magicDNSEnabled,
		strings.Join(status.Health, ";"),
	)
	if peer := findPeer(status, targetHost); peer != nil {
		fmt.Fprintf(os.Stderr, "tsnet target peer %s\n", peerSummary(peer))
	} else {
		fmt.Fprintf(os.Stderr, "tsnet target peer not found for %q\n", targetHost)
		fmt.Fprintf(os.Stderr, "tsnet available peers %s\n", peerList(status))
	}
}

func findPeer(status *ipnstate.Status, targetHost string) *ipnstate.PeerStatus {
	normalizedTarget := normalizeHost(targetHost)
	targetIP, targetIPErr := netip.ParseAddr(strings.Trim(targetHost, "[]"))
	hasTargetIP := targetIPErr == nil
	for _, peer := range status.Peer {
		for _, ip := range peer.TailscaleIPs {
			if hasTargetIP && ip == targetIP {
				return peer
			}
		}
		for _, name := range peerNames(peer) {
			if normalizeHost(name) == normalizedTarget {
				return peer
			}
		}
	}
	return nil
}

func resolveTargetHost(status *ipnstate.Status, targetHost string) string {
	peer := findPeer(status, targetHost)
	if peer == nil {
		return targetHost
	}
	for _, ip := range peer.TailscaleIPs {
		if ip.Is4() {
			return ip.String()
		}
	}
	if len(peer.TailscaleIPs) > 0 {
		return peer.TailscaleIPs[0].String()
	}
	return targetHost
}

func peerNames(peer *ipnstate.PeerStatus) []string {
	names := []string{peer.HostName, peer.DNSName, strings.TrimSuffix(peer.DNSName, ".")}
	dnsName := strings.TrimSuffix(peer.DNSName, ".")
	if firstLabel, _, ok := strings.Cut(dnsName, "."); ok {
		names = append(names, firstLabel)
	}
	return names
}

func missingTargetPeerMessage(status *ipnstate.Status, targetHost string) string {
	if status == nil || findPeer(status, targetHost) != nil {
		return ""
	}
	normalizedTarget := normalizeHost(targetHost)
	targetIP, err := netip.ParseAddr(strings.Trim(targetHost, "[]"))
	if err == nil && isTailscaleNodeIP(targetIP) {
		return fmt.Sprintf(
			"tsnet target %q is not visible in this node's tailnet peer map; check that this app node is in the same tailnet and that Tailscale ACLs/auth-key tags permit access",
			targetHost,
		)
	}
	if status.CurrentTailnet != nil {
		suffix := normalizeHost(status.CurrentTailnet.MagicDNSSuffix)
		if suffix != "" && isShortHostName(normalizedTarget) {
			return fmt.Sprintf(
				"tsnet MagicDNS target %q is not visible in this node's tailnet peer map; check that this app node is in the same tailnet and that Tailscale ACLs/auth-key tags permit access",
				targetHost,
			)
		}
		if suffix != "" && (normalizedTarget == suffix || strings.HasSuffix(normalizedTarget, "."+suffix)) {
			return fmt.Sprintf(
				"tsnet MagicDNS target %q is not visible in this node's tailnet peer map; check that this app node is in the same tailnet and that Tailscale ACLs/auth-key tags permit access",
				targetHost,
			)
		}
	}
	return ""
}

func isShortHostName(host string) bool {
	return host != "" && !strings.Contains(host, ".") && !strings.Contains(host, ":")
}

func isTailscaleNodeIP(ip netip.Addr) bool {
	for _, prefix := range []string{"100.64.0.0/10", "fd7a:115c:a1e0::/48"} {
		parsed, err := netip.ParsePrefix(prefix)
		if err == nil && parsed.Contains(ip) {
			return true
		}
	}
	return false
}

func peerSummary(peer *ipnstate.PeerStatus) string {
	return fmt.Sprintf(
		"host=%q dns=%q ips=%s online=%t active=%t expired=%t inNetMap=%t inMagicSock=%t inEngine=%t relay=%q curAddr=%q lastSeen=%s lastHandshake=%s os=%q",
		peer.HostName,
		peer.DNSName,
		joinedIPs(peer.TailscaleIPs),
		peer.Online,
		peer.Active,
		peer.Expired,
		peer.InNetworkMap,
		peer.InMagicSock,
		peer.InEngine,
		peer.Relay,
		peer.CurAddr,
		formatTime(peer.LastSeen),
		formatTime(peer.LastHandshake),
		peer.OS,
	)
}

func peerList(status *ipnstate.Status) string {
	if status == nil || len(status.Peer) == 0 {
		return ""
	}
	summaries := make([]string, 0, len(status.Peer))
	for _, peer := range status.Peer {
		summaries = append(summaries, fmt.Sprintf("%s/%s/%s", peer.HostName, strings.TrimSuffix(peer.DNSName, "."), joinedIPs(peer.TailscaleIPs)))
	}
	return strings.Join(summaries, "; ")
}

func normalizeHost(host string) string {
	return strings.TrimSuffix(strings.ToLower(strings.TrimSpace(host)), ".")
}

func formatTime(value time.Time) string {
	if value.IsZero() {
		return ""
	}
	return value.Format(time.RFC3339)
}

func registerAndroidInterfaceGetter() {
	if runtime.GOOS != "android" {
		return
	}
	netmon.RegisterInterfaceGetter(androidInterfaces)
}

func androidInterfaces() ([]netmon.Interface, error) {
	if ifaces := androidInterfacesFromRouteProbe(); len(ifaces) > 0 {
		return ifaces, nil
	}
	return []netmon.Interface{
		{
			Interface: &net.Interface{
				Index: 1,
				MTU:   1500,
				Name:  "android0",
				Flags: net.FlagUp,
			},
		},
	}, nil
}

func androidInterfacesFromRouteProbe() []netmon.Interface {
	var addrs []net.Addr
	for _, target := range []struct {
		network string
		address string
		bits    int
	}{
		{"udp4", "8.8.8.8:53", 32},
		{"udp4", "1.1.1.1:53", 32},
		{"udp6", "[2001:4860:4860::8888]:53", 128},
		{"udp6", "[2606:4700:4700::1111]:53", 128},
	} {
		conn, err := net.DialTimeout(target.network, target.address, time.Second)
		if err != nil {
			continue
		}
		localAddr := conn.LocalAddr()
		conn.Close()
		udpAddr, ok := localAddr.(*net.UDPAddr)
		if !ok {
			continue
		}
		ip, ok := netip.AddrFromSlice(udpAddr.IP)
		if !ok || !ip.IsValid() || ip.IsLoopback() {
			continue
		}
		addrs = append(addrs, netAddr(netip.PrefixFrom(ip.Unmap(), target.bits)))
	}
	if len(addrs) == 0 {
		return nil
	}
	return []netmon.Interface{androidInterface(1, "android-route", addrs)}
}

func androidInterface(index int, name string, addrs []net.Addr) netmon.Interface {
	return netmon.Interface{
		Interface: &net.Interface{
			Index: index,
			MTU:   1500,
			Name:  name,
			Flags: net.FlagUp | net.FlagBroadcast | net.FlagMulticast,
		},
		AltAddrs: addrs,
	}
}

func netAddr(prefix netip.Prefix) net.Addr {
	addr := prefix.Addr()
	bits := 128
	if addr.Is4() {
		bits = 32
	}
	return &net.IPNet{
		IP:   net.IP(addr.AsSlice()),
		Mask: net.CIDRMask(prefix.Bits(), bits),
	}
}
