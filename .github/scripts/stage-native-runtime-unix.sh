#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <linux|macos> <classifier> <main-lib>" >&2
  exit 1
fi

platform="$1"
classifier="$2"
main_lib="$3"

repo_root="$(pwd)"
native_dir="$repo_root/native/$classifier"
licenses_dir="$native_dir/licenses"

mkdir -p "$native_dir" "$licenses_dir"

cp "$repo_root/LICENSE" "$licenses_dir/LGPL-3.0.txt"
cp "$repo_root/THIRD_PARTY_NOTICES.md" "$licenses_dir/THIRD_PARTY_NOTICES.md"

if compgen -G "$repo_root/build/native/lib/*" > /dev/null; then
  cp "$repo_root"/build/native/lib/* "$native_dir"/
fi

copy_if_exists() {
  local source_path="$1"
  local target_path="${2:-$native_dir/$(basename "$source_path")}"
  if [[ -n "$source_path" && -f "$source_path" ]]; then
    cp -L "$source_path" "$target_path"
  fi
}

copy_notice_if_exists() {
  local source_path="$1"
  local target_name="$2"
  if [[ -n "$source_path" && -f "$source_path" ]]; then
    cp -L "$source_path" "$licenses_dir/$target_name"
  fi
}

stage_linux_runtime() {
  mapfile -t runtime_deps < <(
    ldd "$native_dir/$main_lib" |
      awk '/=> \// { print $3 }' |
      grep -E '/lib(gmp|mpfr)\.so'
  )

  for dep in "${runtime_deps[@]}"; do
    copy_if_exists "$dep"
  done

  copy_notice_if_exists "/usr/share/common-licenses/LGPL-3" "LGPL-3.0-system-copy.txt"
  copy_notice_if_exists "/usr/share/doc/libgmp10/copyright" "gmp-copyright.txt"
  copy_notice_if_exists "/usr/share/doc/libmpfr6/copyright" "mpfr-copyright.txt"
}

stage_macos_runtime() {
  local main_lib_path="$native_dir/$main_lib"
  local gmp_ref
  local mpfr_ref
  local gmp_name
  local mpfr_name
  local mpfr_lib_path
  local mpfr_gmp_ref
  local gmp_prefix
  local mpfr_prefix

  gmp_ref="$(otool -L "$main_lib_path" | awk '/libgmp.*\.dylib/ { print $1; exit }')"
  mpfr_ref="$(otool -L "$main_lib_path" | awk '/libmpfr.*\.dylib/ { print $1; exit }')"

  if [[ -z "$gmp_ref" ]]; then
    gmp_prefix="$(brew --prefix gmp)"
    gmp_ref="$(find -L "$gmp_prefix/lib" -maxdepth 1 -type f -name 'libgmp*.dylib' | sort | head -n1)"
  fi
  if [[ -z "$mpfr_ref" ]]; then
    mpfr_prefix="$(brew --prefix mpfr)"
    mpfr_ref="$(find -L "$mpfr_prefix/lib" -maxdepth 1 -type f -name 'libmpfr*.dylib' | sort | head -n1)"
  fi

  if [[ -z "$gmp_ref" || -z "$mpfr_ref" ]]; then
    echo "failed to resolve macOS GMP/MPFR runtime libraries" >&2
    exit 1
  fi

  copy_if_exists "$gmp_ref"
  copy_if_exists "$mpfr_ref"

  gmp_name="$(basename "$gmp_ref")"
  mpfr_name="$(basename "$mpfr_ref")"

  install_name_tool -id "@loader_path/$gmp_name" "$native_dir/$gmp_name"
  install_name_tool -id "@loader_path/$mpfr_name" "$native_dir/$mpfr_name"

  if otool -L "$main_lib_path" | grep -Fq "$gmp_ref"; then
    install_name_tool -change "$gmp_ref" "@loader_path/$gmp_name" "$main_lib_path"
  fi
  if otool -L "$main_lib_path" | grep -Fq "$mpfr_ref"; then
    install_name_tool -change "$mpfr_ref" "@loader_path/$mpfr_name" "$main_lib_path"
  fi

  mpfr_lib_path="$native_dir/$mpfr_name"
  mpfr_gmp_ref="$(otool -L "$mpfr_lib_path" | awk '/libgmp.*\.dylib/ { print $1; exit }')"
  if [[ -n "$mpfr_gmp_ref" && "$mpfr_gmp_ref" != "@loader_path/$gmp_name" ]]; then
    install_name_tool -change "$mpfr_gmp_ref" "@loader_path/$gmp_name" "$mpfr_lib_path"
  fi

  gmp_prefix="$(brew --prefix gmp)"
  mpfr_prefix="$(brew --prefix mpfr)"

  copy_notice_if_exists "$(find "$gmp_prefix" -path '*/share/doc/*' -type f \( -iname 'COPYING*' -o -iname 'LICENSE*' \) | sort | head -n1)" "gmp-license.txt"
  copy_notice_if_exists "$(find "$mpfr_prefix" -path '*/share/doc/*' -type f \( -iname 'COPYING*' -o -iname 'LICENSE*' \) | sort | head -n1)" "mpfr-license.txt"
}

case "$platform" in
  linux)
    stage_linux_runtime
    ;;
  macos)
    stage_macos_runtime
    ;;
  *)
    echo "unsupported platform: $platform" >&2
    exit 1
    ;;
esac

ls -la "$native_dir"
