#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
repo='lolokeksu09/SPECTRA'
keydir="$HOME/.spectra-signing"
if [ ! -d /data/data/com.termux/files/usr ]; then
  printf '%s\n' 'Запустите этот скрипт в Termux на Android.' >&2; exit 1
fi
pkg install -y gh openjdk-17 python coreutils
if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  gh auth login --hostname github.com --git-protocol https --web
fi
# Verify repository access and inspect secret names; never print secret values.
secrets=$(gh secret list --repo "$repo")
if printf '%s\n' "$secrets" | awk '{print $1}' | grep -Eq '^SPECTRA_(KEYSTORE_B64|STORE_PASSWORD|KEY_ALIAS)$'; then
  printf '%s\n' 'В GitHub уже настроены секреты SPECTRA. Остановка: секреты не перезаписаны.' >&2; exit 1
fi
mkdir -p "$keydir"
chmod 700 "$keydir"
if [ ! -f "$keydir/spectra.jks" ]; then
  if printf '%s\n' "$secrets" | awk '{print $1}' | grep -Eq '^SPECTRA_(KEYSTORE_B64|STORE_PASSWORD|KEY_ALIAS)$'; then
    printf '%s\n' 'В GitHub уже есть секреты подписи, а локальный ключ не найден. Остановка: новый ключ не создан.' >&2
    exit 1
  fi
  if [ -e "$keydir/password.txt" ]; then
    printf '%s\n' 'Найдена неполная локальная настройка. Ничего не перезаписано.' >&2; exit 1
  fi
  python -c 'import secrets; print(secrets.token_urlsafe(36))' > "$keydir/password.txt"
  keytool -genkeypair -noprompt -keystore "$keydir/spectra.jks" -storetype PKCS12 \
    -storepass:file "$keydir/password.txt" -keypass:file "$keydir/password.txt" \
    -alias spectra -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=SPECTRA Android Signing' >/dev/null
fi
if [ ! -f "$keydir/password.txt" ]; then
  printf '%s\n' 'Пароль локального ключа не найден. Секреты не изменены.' >&2; exit 1
fi
keytool -list -keystore "$keydir/spectra.jks" -storetype PKCS12 -storepass:file "$keydir/password.txt" -alias spectra >/dev/null
base64 "$keydir/spectra.jks" | tr -d '\n' | gh secret set SPECTRA_KEYSTORE_B64 --repo "$repo"
gh secret set SPECTRA_STORE_PASSWORD --repo "$repo" < "$keydir/password.txt"
printf '%s' spectra | gh secret set SPECTRA_KEY_ALIAS --repo "$repo"
gh workflow run android.yml --repo "$repo" --ref main
printf '\n%s\n' 'Ключ сохранён в ~/.spectra-signing. Сохрани резервную копию папки в безопасном месте.' 'Никому не отправляй ключ и пароль. GitHub Actions запускает подписанную сборку.'
