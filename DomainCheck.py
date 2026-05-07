# ! Bu araç @Kraptor123 tarafından | @Cs-kraptor için yazılmıştır.
#!/usr/bin/env python3
# coding: utf-8

from cloudscraper import CloudScraper
from urllib.parse import urlparse
import os, re, logging

logging.basicConfig(level=logging.INFO, format='[%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

class MainUrlUpdater:
    def __init__(self, base_dir="."):
        self.base_dir = base_dir
        self.oturum   = CloudScraper()

    @property
    def eklentiler(self):
        # Eklenti klasörlerini listeler
        try:
            candidates = [
                dosya for dosya in os.listdir(self.base_dir)
                if os.path.isdir(os.path.join(self.base_dir, dosya))
                   and not dosya.startswith(".")
                   and dosya not in {"gradle", "__Temel", "HQPorner", "xVideos", "PornHub", "Xhamster", "Chatrubate"}
            ]
            return sorted(candidates)
        except FileNotFoundError:
            return []

    def _kt_dosyasini_bul(self, dizin, dosya_adi):
        # Klasör içinde .kt dosyasını arar
        start = os.path.join(self.base_dir, dizin)
        for kok, alt_dizinler, dosyalar in os.walk(start):
            if dosya_adi in dosyalar:
                return os.path.join(kok, dosya_adi)
        return None

    @property
    def kt_dosyalari(self):
        # Ana eklenti dosyalarının yollarını toplar
        result = []
        for eklenti in self.eklentiler:
            kt_path = self._kt_dosyasini_bul(eklenti, f"{eklenti}.kt")
            if kt_path:
                result.append(kt_path)
        return result

    def _mainurl_bul(self, kt_dosya_yolu):
        # Dosyadan mainUrl değerini çeker
        try:
            with open(kt_dosya_yolu, "r", encoding="utf-8") as file:
                icerik = file.read()
                if m := re.search(r'override\s+var\s+mainUrl\s*=\s*["\']([^"\']+)["\']', icerik):
                    return m[1]
        except Exception:
            logger.error(f"Dosya okunurken hata: {kt_dosya_yolu}")
        return None

    def _mainurl_guncelle(self, kt_dosya_yolu, eski_url, yeni_url):
        # mainUrl atamasını günceller
        try:
            with open(kt_dosya_yolu, "r+", encoding="utf-8") as file:
                icerik = file.read()
                yeni_icerik, adet = re.subn(
                    r'(override\s+var\s+mainUrl\s*=\s*["\'])([^"\']+)(["\'])',
                    r'\1' + yeni_url + r'\3',
                    icerik,
                    flags=re.IGNORECASE
                )
                if adet == 0:
                    yeni_icerik = icerik.replace(eski_url, yeni_url)

                if yeni_icerik == icerik:
                    return False

                file.seek(0)
                file.write(yeni_icerik)
                file.truncate()
            return True
        except Exception:
            return False

    def _gradle_guncelle(self, build_gradle_yolu, yeni_url):
        # Versiyon artırır ve iconUrl içindeki domaini günceller
        try:
            yeni_domain = self._sadece_domain_al(yeni_url)
            with open(build_gradle_yolu, "r+", encoding="utf-8") as file:
                icerik = file.read()

                # Versiyon artırma
                if v_match := re.search(r'(^\s*version\s*=\s*)(\d+)', icerik, flags=re.MULTILINE):
                    eski_v = int(v_match.group(2))
                    yeni_v = eski_v + 1
                    icerik = icerik.replace(v_match.group(0), f"{v_match.group(1)}{yeni_v}")
                else:
                    yeni_v = None

                # iconUrl içindeki domaini güncelleme (favicon parametresi)
                # url=https://... kısmını yakalayıp yeni domainle değiştirir
                icerik = re.sub(r'(url=https?://)([^&"\s]+)', r'\1' + yeni_domain.replace("https://", "").replace("http://", ""), icerik)

                file.seek(0)
                file.write(icerik)
                file.truncate()
                return yeni_v
        except Exception:
            return None

    def _sadece_domain_al(self, url, https_tercih=True):
        # URL'den temiz domain kısmını çeker
        if not url: return None
        try:
            parsed = urlparse(url if "://" in url else f"http://{url}")
            scheme = "https" if https_tercih else parsed.scheme
            return f"{scheme}://{parsed.netloc}"
        except Exception:
            return None

    @property
    def mainurl_listesi(self):
        # Geçerli mainUrl'i olan dosyaları listeler
        result = {}
        for kt_dosya_yolu in self.kt_dosyalari:
            mainurl = self._mainurl_bul(kt_dosya_yolu)
            if mainurl:
                result[kt_dosya_yolu] = mainurl
        return result

    def guncelle(self):
        for dosya, mainurl in self.mainurl_listesi.items():
            try:
                relative_path = os.path.relpath(dosya, self.base_dir)
                eklenti_adi = relative_path.split(os.sep)[0]
            except Exception:
                continue

            logger.info(f"[~] Kontrol Ediliyor : {eklenti_adi}")

            mainurl_temiz = self._sadece_domain_al(mainurl)
            if not mainurl_temiz: continue

            try:
                istek = self.oturum.get(mainurl_temiz, allow_redirects=True, timeout=15)
                final_url = istek.url.rstrip('/')
            except Exception:
                logger.warning(f"[!] Bağlantı hatası: {mainurl_temiz}")
                continue

            yeni_domain = self._sadece_domain_al(final_url)
            if not yeni_domain or mainurl_temiz == yeni_domain:
                continue

            # Güncelleme işlemleri
            if self._mainurl_guncelle(dosya, mainurl, yeni_domain):
                gradle_yolu = os.path.join(self.base_dir, eklenti_adi, "build.gradle.kts")
                yeni_v = self._gradle_guncelle(gradle_yolu, yeni_domain)
                logger.info(f"[»] {mainurl} -> {yeni_domain} (v{yeni_v if yeni_v else '?'})")

if __name__ == "__main__":
    updater = MainUrlUpdater(base_dir=".")
    updater.guncelle()