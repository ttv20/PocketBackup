#!/usr/bin/env python3
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

BASE_URL = os.environ.get("TERMUX_MAIN_URL", "https://packages.termux.dev/apt/termux-main")
ARCH = "aarch64"
INDEX_URL = f"{BASE_URL}/dists/stable/main/binary-{ARCH}/Packages"
ROOT_PACKAGES = ["rsync", "openssh", "openssl-tool", "zstd"]
PROJECT_DIR = Path(__file__).resolve().parents[1]
CACHE_DIR = PROJECT_DIR / ".termux-package-cache"
OUT_DIR = PROJECT_DIR / "native" / "out" / "arm64-v8a"
TERMUX_PREFIX = Path("data/data/com.termux/files/usr")


def fetch_url(url: str) -> bytes:
    with urllib.request.urlopen(url) as response:
        return response.read()


def parse_index(text: str) -> dict[str, dict[str, str]]:
    packages: dict[str, dict[str, str]] = {}
    for record in text.strip().split("\n\n"):
        fields: dict[str, str] = {}
        current_key = None
        for line in record.splitlines():
            if line.startswith(" ") and current_key:
                fields[current_key] += "\n" + line[1:]
                continue
            key, _, value = line.partition(": ")
            current_key = key
            fields[key] = value
        package = fields.get("Package")
        if package:
            packages[package] = fields
    return packages


def dependency_names(depends: str) -> list[str]:
    names: list[str] = []
    if not depends:
        return names
    for dependency in depends.split(","):
        alternatives = [part.strip() for part in dependency.split("|")]
        for alternative in alternatives:
            name = re.sub(r"\s*\(.*?\)", "", alternative).strip()
            if name:
                names.append(name)
                break
    return names


def resolve_closure(packages: dict[str, dict[str, str]], roots: list[str]) -> list[str]:
    resolved: list[str] = []
    seen = set()
    stack = list(reversed(roots))
    while stack:
        package = stack.pop()
        if package in seen:
            continue
        if package not in packages:
            print(f"warning: dependency not found in index: {package}", file=sys.stderr)
            continue
        seen.add(package)
        resolved.append(package)
        deps = dependency_names(packages[package].get("Depends", ""))
        stack.extend(reversed([dep for dep in deps if dep not in seen]))
    return resolved


def download_package(package: str, fields: dict[str, str]) -> Path:
    filename = fields["Filename"]
    expected_sha256 = fields["SHA256"]
    destination = CACHE_DIR / Path(filename).name
    if not destination.exists():
        url = f"{BASE_URL}/{filename}"
        print(f"download {package}: {url}")
        destination.write_bytes(fetch_url(url))
    actual_sha256 = hashlib.sha256(destination.read_bytes()).hexdigest()
    if actual_sha256 != expected_sha256:
        destination.unlink(missing_ok=True)
        raise RuntimeError(f"sha256 mismatch for {package}: {actual_sha256} != {expected_sha256}")
    return destination


def extract_deb(deb: Path, destination: Path) -> None:
    members = subprocess.check_output(["ar", "t", str(deb)], text=True).splitlines()
    data_member = next((name for name in members if name.startswith("data.tar.")), None)
    if data_member is None:
        raise RuntimeError(f"{deb} has no data tar member")
    with subprocess.Popen(["ar", "p", str(deb), data_member], stdout=subprocess.PIPE) as ar_proc:
        assert ar_proc.stdout is not None
        if data_member.endswith(".xz"):
            tar_args = ["tar", "-xJ", "-C", str(destination)]
        elif data_member.endswith(".gz"):
            tar_args = ["tar", "-xz", "-C", str(destination)]
        elif data_member.endswith(".zst"):
            tar_args = ["tar", "--zstd", "-x", "-C", str(destination)]
        else:
            tar_args = ["tar", "-x", "-C", str(destination)]
        tar_proc = subprocess.run(tar_args, stdin=ar_proc.stdout, check=True)
        ar_proc.stdout.close()
        if ar_proc.wait() != 0 or tar_proc.returncode != 0:
            raise RuntimeError(f"failed to extract {deb}")


def copy_if_exists(source: Path, destination: Path) -> None:
    if not source.exists():
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def copy_tree_contents(source: Path, destination: Path) -> None:
    if not source.exists():
        return
    for path in source.rglob("*"):
        if path.is_file():
            copy_if_exists(path, destination / path.relative_to(source))


def copy_libraries(source: Path, destination: Path) -> None:
    if not source.exists():
        return
    for path in source.rglob("*"):
        if not path.is_file():
            continue
        name = path.name
        rel = path.relative_to(source)
        is_shared_library = name.endswith(".so") or ".so." in name
        is_runtime_module = rel.parts[0] in {"engines-3", "ossl-modules"} and name.endswith(".so")
        if is_shared_library or is_runtime_module:
            copy_if_exists(path, destination / rel)


def stage_from_root(root: Path, package_names: list[str], packages: dict[str, dict[str, str]]) -> None:
    preserved_tsnet = None
    tsnet_path = OUT_DIR / "tsnet-nc"
    if tsnet_path.exists():
        preserved_tsnet = tsnet_path.read_bytes()

    shutil.rmtree(OUT_DIR, ignore_errors=True)
    (OUT_DIR / "lib").mkdir(parents=True, exist_ok=True)
    prefix = root / TERMUX_PREFIX

    for name in ["rsync", "ssh", "ssh-keygen", "ssh-keyscan", "scp", "sftp"]:
        copy_if_exists(prefix / "bin" / name, OUT_DIR / name)
    copy_libraries(prefix / "lib", OUT_DIR / "lib")
    copy_tree_contents(prefix / "etc" / "ssh", OUT_DIR / "etc" / "ssh")

    if preserved_tsnet is not None:
        tsnet_path.write_bytes(preserved_tsnet)
        tsnet_path.chmod(0o755)

    docs_dir = OUT_DIR / "termux-docs"
    for package in package_names:
        copyright_file = prefix / "share" / "doc" / package / "copyright"
        if copyright_file.exists():
            copy_if_exists(copyright_file, docs_dir / package / "copyright")

    for executable in ["rsync", "ssh", "ssh-keygen", "ssh-keyscan", "scp", "sftp"]:
        path = OUT_DIR / executable
        if path.exists():
            path.chmod(0o755)

    manifest_lines = [
        "Termux package snapshot for bundled Android arm64 native tools",
        f"Repository: {BASE_URL}",
        f"Index: {INDEX_URL}",
        "",
    ]
    for package in package_names:
        fields = packages[package]
        manifest_lines += [
            f"Package: {package}",
            f"Version: {fields.get('Version', '')}",
            f"Filename: {fields.get('Filename', '')}",
            f"SHA256: {fields.get('SHA256', '')}",
            f"Homepage: {fields.get('Homepage', '')}",
            "",
        ]
    (OUT_DIR / "termux-packages.txt").write_text("\n".join(manifest_lines), encoding="utf-8")


def main() -> int:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    print(f"fetch index: {INDEX_URL}")
    packages = parse_index(fetch_url(INDEX_URL).decode("utf-8"))
    package_names = resolve_closure(packages, ROOT_PACKAGES)
    print("packages: " + ", ".join(package_names))
    package_files = [download_package(name, packages[name]) for name in package_names]

    with tempfile.TemporaryDirectory(prefix="rsync-backup-termux-") as temp:
        root = Path(temp)
        for package_file in package_files:
            extract_deb(package_file, root)
        stage_from_root(root, package_names, packages)

    required = ["rsync", "ssh"]
    missing = [name for name in required if not (OUT_DIR / name).exists()]
    if missing:
        raise RuntimeError(f"missing staged executables: {', '.join(missing)}")
    print(f"staged native files in {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
