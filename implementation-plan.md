# Plán implementace: Vzato pro Android

Stav: návrh, 21. 11. 2026. Zdroj pravdy pro chování je specifikace iOS verze
(`../MedRemind/specification.md`). Tento plán říká, jak stejnou aplikaci postavit pro Android
telefony a tablety, v jakém pořadí a co se liší. Kde se Android chová jinak, rozhodnutí se zapíše
sem a do specifikace.

## 0. Rozhodnutí

| Oblast | Volba | Poznámka |
|---|---|---|
| Jazyk a UI | **Kotlin + Jetpack Compose** (Material 3), rozhodnuto 21. 11. 2026 | Nativní Android, nejbližší SwiftUI. Kód s iOS se nesdílí; přenáší se logika, pravidla a testy. |
| Závislosti | Jen Kotlin, kotlinx-coroutines a AndroidX (Compose, Room, WorkManager, Glance, Navigation) | Android obdoba Apple frameworků. **Žádné Google Play služby, Firebase, ML Kit, analytika ani knihovny třetích stran** (pravidlo 1 iOS repozitáře a podmínka F-Droidu). |
| Data | Room (SQLite) v zařízení | Schéma odpovídá modelu Core Data verze 8 (stejné názvy polí, výčty jako řetězce, seznamy jako „1,2,3“), aby šel později přenos dat mezi iOS a Androidem souborem. |
| Synchronizace | **Google Drive, skrytá složka aplikace (appDataFolder)**, rozhodnuto 21. 11. 2026; ve v1 zatím jen v zařízení, synchronizace ve fázi B8 | Data v Google účtu uživatele, vývojář k nim nemá přístup, uživatel je v Drive nevidí. Zapíná se v Nastavení (výchozí vypnuto, nic neopustí zařízení bez akce uživatele). Mezi Android zařízeními jednoho uživatele; iPhone ↔ Android jen záložním souborem (B7). Do té doby záloha a obnova do souboru a systémová záloha Androidu (Google One, šifrovaná zámkem obrazovky). |
| Minimum | Android 8.0 (API 26), target = aktuální úroveň, kterou vyžaduje Google Play | API 26 pokrývá téměř všechna používaná zařízení; kanály oznámení existují od 26. |
| Zařízení | Telefony a tablety | Tablety a skládací telefony: postranní panel a seznam + detail jako iPad (adaptivní rozvržení). |
| Distribuce | **Google Play, F-Droid, APK na GitHubu**; rozhodnuto 21. 11. 2026 | Viz fáze A7. |
| Rozsah v1 | **Jádro, pak doplňovat**; rozhodnuto 21. 11. 2026 | Viz fáze A1–A7, rozšíření B1–B7. |
| Repozitář | `../vzato-android`, samostatný veřejný repozitář | Licence MPL-2.0 s hlavičkou v každém souboru `.kt`, název a ikona z licence vyňaty jako u iOS. |
| Identifikátor | `app.vzato` | Stejný jako bundle ID na iOS. |

Pravidla iOS verze platí beze změny: žádný server ani účty, nejde o zdravotnický prostředek (bez
doporučování dávek a interakcí), nic neopustí zařízení bez akce uživatele, klíče a podpisové
soubory mimo git, čtyři jazyky v každé změně, kalendářní aritmetika (`java.time`), nikdy ruční
počítání sekund.

### Data připravená na synchronizaci (platí od A2)

Aby šlo v B8 synchronizovat bez přestavby databáze:

- Každý záznam má `id` (UUID) a `updatedAt`; nic se neidentifikuje podle pořadí nebo čísla řádku.
- Záznam dávky (`DoseEvent`) a činnosti (`ActivityEntry`) se jen přidávají; oprava dávky je nový
  stav téhož záznamu, smazání jen u `reset` (stejně jako na iOS) a zapíše se jako tombstone.
- Duplicitní záznamy jedné dávky z různých zařízení řeší stejné deterministické pravidlo jako iOS
  (`DoseEvent.preferred`: vzato vyhrává, pak nejdřívější užití, nejdřívější zápis, id).
- Zásoba se nesynchronizuje jako číslo, ale odvozuje z posledního spočítání a záznamů dávek, aby
  dvě zařízení neodečetla stejnou tabletu dvakrát.
- Každé zařízení má vlastní `deviceId`, zapisuje se ke každé změně (záznam činností už ho má).

### Struktura repozitáře

```
vzato-android/
  core/            čistý Kotlin (JVM modul): plán dávek, stavy, záznam, zásoba, kontrola, připomínky,
                   formátování množství. Bez Androidu, testuje se na JVM.
  app/             Android aplikace: Room, služby, oznámení a alarmy, Compose obrazovky
    src/main/res/values{,-cs,-sk,-hu}/strings.xml
  scripts/         převod String Catalogu z iOS do strings.xml, snímky obrazovky
  fastlane/metadata/android/<jazyk>/   texty a snímky pro F-Droid (a podklad pro Google Play)
```

## 1. Co přenášíme z iOS (mapa)

| iOS | Android | Poznámka |
|---|---|---|
| Core Data, `Medication`, `Schedule`, `DoseEvent`, `Profile`, `ActivityEntry`, `Organizer`, `MedicationPhoto` | Room entity se stejnými poli | Migrace Room od verze 1, exportované schéma v gitu. |
| `DoseScheduler`, `DoseSlot` | `core`: stejná logika nad `java.time` | Záznam `DoseEvent` vzniká až akcí uživatele; nikdy se nepředvytváří. |
| `DoseLogger` (jediné místo změny zásoby kvůli dávce, `ActivityEntry`) | `DoseLogger` | Množství vzaté dávky (`units`, `unitRaw`) od začátku. |
| `StockService`, `ReconciliationService` | totéž | |
| `ReminderPlanner`, `NotificationService` | `ReminderPlanner` (core) + `AlarmScheduler` (app) | Jiný mechanismus doručení, viz A4. |
| `DoseFormatter`, `DoseUnit`, plurály v katalogu | `DoseFormatter` + `<plurals>` v `strings.xml` | Čeština a slovenština potřebují `few`/`many`, zlomky „½ tablety“. |
| `CSVExport`, `DoctorSummary` (PDF) | totéž, PDF přes `android.graphics.pdf.PdfDocument` | Stejné sloupce CSV jako iOS. |
| `PhotoStore` (ImageIO) | `ImageDecoder` / `BitmapFactory`, JPEG zmenšený na 2000 px | Fotky v souborech aplikace, ne v galerii. |
| `Medicines.index` + `MedicineIndex` | stejný soubor v `assets`, `java.util.zip.Inflater` | Index se jen zkopíruje z iOS repozitáře. |
| `LeafletService` (PDF ze SÚKL na klepnutí) | totéž | Jediné oprávnění `INTERNET`, jen pro tuto akci. |
| WidgetKit + App Intents | Jetpack Glance | Tlačítko Vzato ve widgetu. |
| Testy Swift Testing (`TestStore`, `SpyNotifier`, Praha, pondělí 2. 11. 2026) | JUnit testy `core` a Room v paměti | Testovací případy přepsat 1:1, stejný kalendář a data. |

## 2. Fáze

Každá fáze končí sestavením, zelenými testy a vyzkoušením na emulátoru (Pixel 9a, Pixel Tablet)
a pokud možno na skutečném telefonu.

### Fáze A0 – Projekt a nástroje

**Stav 21. 11. 2026: hotovo.** Gradle 9.5, AGP 9.3.1, Kotlin 2.2.20, Compose BOM 2025.09; moduly
`core` (JVM) a `app`; adaptivní ikona E jako vektor; vlastní barvy bez fialového nádechu Material;
490 textů z iOS převedeno skriptem (plurály cs/sk s `few`/`many`); první část jádra
(`DoseUnit`, `DoseFormatter`) s testy; aplikace běží na emulátoru Pixel 9a česky. Lint bez chyb.

Gradle (Kotlin DSL) s katalogem verzí, moduly `core` a `app`, Compose, Room, ikona (zdroj SVG z
`../MedRemind/Design/icon`, adaptivní ikona s monochromatickou vrstvou), barvy směru C (zelená,
tmavý režim). `CLAUDE.md` a `README.md` s postupem sestavení, `.gitignore` (keystore,
`local.properties`, `build/`). Skript převodu String Catalogu (`scripts/xcstrings-to-android.py`):
vezme `Localizable.xcstrings` z iOS a vytvoří `strings.xml` pro en/cs/sk/hu včetně plurálů, aby
texty a překlady zůstaly shodné.

### Fáze A1 – Jádro logiky (`core`)

**Stav 21. 11. 2026: hotovo.** Objektový model jako Core Data verze 8 (`Model.kt`), `DoseStore`
jako rozhraní úložiště (Room v A2, paměť v testech), `DoseScheduler`, `ScheduleRules`, `DoseGuard`,
`DoseLogger`, `StockService`, `ReconciliationService`, `ReminderPlanner`, `TodaySnapshot`,
`DoseSummary`, archivace, `AmountParser`, varování před dojitím. 85 testů na JVM, převzatých z
`VzatoTests` (scheduler, zásoba, ochrana dávky, kontrola zásob, plánovač, opakování, letní čas,
Dnes, souhrny, „Už můžete jíst“, množství dávky) a jeden navíc pro den změny času. Texty do
záznamu činností dodává aplikace (`ActivityTexts`). Zbylé testy z iOS přijdou s funkcemi, ke
kterým patří: `DoseUnitTests` a `MedicationFactoryTests` (A3, řetězce a průvodce),
`DeliveryIssueTests` (A4), CSV (A5), `DuplicateAndLogTests` (B7/B8, slučování), `OrganizerTests` (B3).

Plán dávek pro den a interval (dny v týdnu, opakování po N dnech/týdnech/měsících, začátek, konec,
archivace), stavy slotu (čeká → zmeškáno po lhůtě), dávky mimo plán, ochrana proti dvojí dávce
(minimální odstup), `ReminderPlanner` (jedno oznámení na čas se všemi léky, opakování, odložení,
„Už můžete jíst“), projekce dojití zásoby, kontrola zásob, formátování množství. Testy převzaté z
`VzatoTests` (scheduler, logger, stock, reconciliation, planner, dose amount). Hotovo, až projdou
stejné případy jako na iOS.

### Fáze A2 – Data a služby

**Stav 21. 11. 2026: hotovo** (ukázková data přesunuta do A3, kde je je kde ukázat). Room verze 1
se stejnými poli jako Core Data model 8, bez cizích klíčů (kvůli budoucí synchronizaci), schéma
v `app/schemas`. `VzatoRepository.edit` pouští změny jednu po druhé v jedné transakci nad
čerstvě načtenými daty; `RoomDoseStore` zapisuje jen změněné řádky. Výchozí profil „Já“, nastavení
se stejnými výchozími hodnotami jako iOS, texty záznamu činností z překladů iOS. 4 testy na
emulátoru (uložení dávky s množstvím, vrácení, změna plánu, smazání léku).

Room entity a DAO, `DoseLogger`, `StockService`, `ReconciliationService`, záznam činností
(`ActivityEntry`, nikdy se needituje ani nemaže), výchozí profil „Já“. Ukázková data pro ladění
(`--sample-data` jako argument intentu v debug buildu).

### Fáze A3 – Prototyp na telefonu: Léky a Dnes

**Stav 21. 11. 2026: hotovo.** Dnes s velkou kartou (po termínu, zmeškaná, příští), tlačítkem
Vzato, Přeskočit, časovou osou dne (podržením Vzato, Přeskočit, Vrátit zpět), lištou Vrátit po
potvrzení, dotazem při dvojí dávce a příliš brzké dávce, dávkou navíc, upozorněním na zásobu a „Jíst
od“. Léky: seznam se zásobou a datem doplnění, archivované s obnovením, formulář (název, síla,
jednotka, množství po čtvrtkách, časy, dny nebo opakování po N dnech/týdnech/měsících, začátek,
konec, jídlo a čekání, barva, poznámka, zásoba a hranice upozornění), archivace a smazání. Úvodní
průvodce (vítejte, název, množství, časy, shrnutí). Spodní lišta na telefonu, boční lišta a
čitelný sloupec na tabletu. Ukázková data v ladicím buildu (`--ez sample-data true`). Odložit
přibude s připomínkami (A4), stejně jako krok s povolením oznámení v průvodci.
Jádro nesmí používat API novější než Java 8 (lint ho nekontroluje): `LocalDate.ofInstant` by na
Androidu před 14 spadlo, nahrazeno.

- Léky: seznam, formulář (název, síla, jednotka, množství po čtvrtkách, časy, dny, jídlo, začátek
  a konec, barva, poznámka), archivace a obnovení.
- Dnes: velká karta (směr C) s tlačítkem **Vzato**, Odložit, Přeskočit, časová osa dne, Vrátit
  po potvrzení, dotaz při dvojí dávce, dávka navíc, obnova při návratu do popředí.
- Úvodní průvodce (vítejte, povolení oznámení s důvodem, první lék ve třech krocích).
- Navigace: spodní lišta na telefonu, postranní panel na tabletu.

### Fáze A4 – Připomínky

**Stav 21. 11. 2026: hotovo, ověřeno na emulátoru** (připomínka přišla na minutu, Vzato z
oznámení zapsalo dávku a zrušilo opakování, odložení z karty přišlo za 10 minut). Rozhodnutí:
**jen `SCHEDULE_EXACT_ALARM`** (uživatel povolí Budíky a připomenutí, Dnes na to upozorní),
**ne `USE_EXACT_ALARM`**: Google Play ho podle dokumentace nechává budíkům a kalendářům a
připomínky léků výslovně nejmenuje; bez povolení přijde připomínka s odchylkou několika minut.
Jeden budík (`ReminderEngine`): plán z `ReminderPlanner` (stejný jako iOS, bez limitu 64), budík
na nejbližší okamžik (připomínka, opakování, odložení, „Už můžete jíst“, varování před dojitím v
9:00), po zazvonění se ukáže nejnovější pokus každého času (telefon, který prospal opakování,
neukáže hromadu) a nastaví se další. Odložení a jídla v `ReminderStore`. Akce Vzato / Odložit /
Přeskočit z oznámení přes `DoseLogger` (zdroj oznámení), „Nezaznamenáno“ při dávce těsně po
jiné. Oznámení dávek zaznamenaných jinde zmizí. Přestavba po každé změně dat, při restartu,
změně času, pásma, jazyka, aktualizaci a změně povolení budíků. Kanály: Připomínky léků
(vysoká důležitost), Už můžete jíst, Zásoby (tiché). Krok s povolením oznámení v průvodci,
varování na Dnes. Ladicí přepínač `--ez reminder-test true` přidá lék s dávkou za dvě minuty.
Zbývá: vlastní zvuky (iOS je má), varování před úspornými režimy výrobců, průchod přes Nerušit
(nastavení kanálu) – v A6 s obrazovkou Nastavení.

Největší rozdíl proti iOS: Android nemá „naplánuj 64 oznámení dopředu“, a výrobci telefonů
agresivně uspávají aplikace. Postup:

- `ReminderPlanner` z jádra spočítá plán; `AlarmManager` drží **jen nejbližší budík** a po jeho
  zaznění se naplánuje další. Žádný limit 64, plán se přestaví při spuštění, každé změně, akci
  z oznámení, restartu telefonu (`BOOT_COMPLETED`), změně času a pásma (`TIME_SET`,
  `TIMEZONE_CHANGED`), aktualizaci aplikace a jednou denně přes WorkManager.
- Přesné budíky: `setExactAndAllowWhileIdle` s oprávněním `SCHEDULE_EXACT_ALARM` (od Androidu 14
  ho uživatel musí povolit; průvodce i Nastavení vysvětlí proč). Bez povolení náhradní
  `setWindow` s oknem několika minut a varování v Nastavení, stejně jako iOS varuje před vypnutými
  časově citlivými oznámeními.
- Kanály oznámení: Připomínky léků (vysoká důležitost, zvuk Vzato), Už můžete jíst (jemný zvuk),
  Zásoby (tiché). Uživatel může kanálu povolit průchod přes Nerušit; Nastavení na to upozorní a
  otevře nastavení kanálu.
- Oprávnění `POST_NOTIFICATIONS` (Android 13+) při prvním léku s časem, červené varování na Dnes
  jako na iOS.
- Akce Vzato / Odložit / Přeskočit přes `BroadcastReceiver` (`goAsync`), opakování nepotvrzené
  dávky, zrušení doručených oznámení po potvrzení kdekoli, „Nezaznamenáno: už bylo vzato v …“.
- Úsporné režimy výrobců (Samsung, Xiaomi, Huawei…): Nastavení ukáže, když je Vzato omezené
  optimalizací baterie, a nabídne výjimku s vysvětlením.
- Testy: plánování na JVM, doručení instrumentovaným testem a ručně na telefonu přes noc.

### Fáze A5 – Historie a zásoby

**Stav 21. 11. 2026: hotovo.** Historie: měsíční kalendář s barevnou tečkou dne (zelená vzato,
červená zmeškáno, šedá čeká), dávky zvoleného dne s opravou podržením (dodatečně vzatá dávka se
zapíše na svůj čas), součty měsíce a dodržování. Export všech dávek do CSV (stejné sloupce jako
iOS, zmeškané dávky, profil, množství; BOM kvůli Excelu) přes systémové sdílení a `FileProvider`.
Kontrola zásob z formuláře léku („Spočítat a zkontrolovat zásobu“) s výsledkem a doplněním
chybějících dávek. `CsvExport` v jádře se 4 testy. Záznam činností a jeho export přijdou s
obrazovkou Nastavení (A6).

Historie: měsíční kalendář se stavem dní, oprava dne, přehled dodržování, export CSV (sdílení
souboru přes `Intent.ACTION_SEND`). Zásoby: počet kusů, odečítání, upozornění na nízkou zásobu
a dojití, „Doplnit do“, kontrola zásob s doplněním chybějících záznamů. Záznam činností
v Nastavení.

### Fáze A6 – Nastavení, tablet, jazyky, záloha

**Stav 21. 11. 2026: hotovo.** Nastavení: stav oznámení a přesných budíků, upozornění na úsporu
baterie (odkaz na seznam aplikací, ne přímá žádost: tu Google Play povoluje jen některým
aplikacím), odložení, opakování, lhůta zmeškání, Nerušit (odkaz na nastavení kanálu Připomínky
léků), Obnovit připomínky; zvuky z iOS (převedené `scripts/convert-sounds.sh` do WAV, kanál pro
každý zvuk, protože Android zvuk kanálu nemění); zásoby; vzhled (systém, světlý, tmavý, i se
stavovou lištou); jazyk (odkaz na jazyk aplikace v systému); záloha; záznam činností s exportem
CSV; nápověda (kroky z iOS, které platí na Androidu, a čtyři kroky pro Android); znovu průvodce;
Zdarma pro všechny, verze, upozornění „není zdravotnický prostředek“, zásady soukromí, zdrojový
kód. Záloha a obnova jednoho souboru JSON s názvy polí iOS (`docs/backup-format.md`) přes
systémový výběr souboru; test přenosu na „jiný telefon“. Tablet a čtyři jazyky hotové od A3.
Nové texty jen pro Android jsou v `values*/strings.xml` ve čtyřech jazycích.

Nastavení (vzhled, odložení, lhůta zmeškání, opakování, zásoby, zvuky, upozornění na systémové
volby), nápověda v aplikaci a odkaz na vzato.app, „Zdarma pro všechny“, upozornění, že nejde
o zdravotnický prostředek. Rozvržení pro tablety a skládací telefony. Čeština, slovenština,
angličtina, maďarština, volba jazyka aplikace v systému (Android 13+). **Záloha a obnova** do
jednoho souboru (data + fotky) přes systémový výběr souboru; formát popsaný v repozitáři, aby ho
později uměl načíst i iOS.

### Fáze A7 – Beta a vydání

- **Podpis:** vlastní klíč pro GitHub a F-Droid (pokud F-Droid ověří reprodukovatelné sestavení,
  použije náš podpis), Google Play App Signing s nahrávacím klíčem. Keystore a hesla mimo git.
  Pozor: APK z Google Play a z GitHubu budou podepsané různými klíči, mezi nimi nejde aktualizovat
  bez přeinstalace (do README).
- **Google Play:** vývojářský účet (25 USD jednorázově, ověření totožnosti), formulář Data safety
  („žádná data se neshromažďují“), prohlášení pro zdravotní aplikace (Health apps declaration),
  zásady soukromí na vzato.app, **uzavřený test s alespoň 12 testery po 14 dní** (podmínka pro
  nové osobní účty), pak produkce.
- **F-Droid:** žádost do `fdroiddata`, metadata a snímky ve `fastlane/metadata/android`,
  sestavení ze zdrojového kódu bez proprietárních knihoven, ideálně reprodukovatelné.
- **GitHub Releases:** podepsané APK s kontrolním součtem.
- **Web vzato.app:** odkazy na Google Play a F-Droid, zásady soukromí doplněné o Android
  (systémová záloha, oprávnění), nápověda s obrázky z Androidu.
- Snímky obrazovky: skript nad emulátorem ve čtyřech jazycích s ukázkovými daty a posunutými
  hodinami, stejné nadpisy jako App Store (`Docs/app-store/screenshot-headlines.tsv`).

## 3. Rozšíření po v1 (pořadí podle zájmu)

| Fáze | Obsah | iOS předloha |
|---|---|---|
| B1 | Profily (děti, zvířata), filtr na Dnes a v Historii | kapitola 12 |
| B2 | Databáze léčiv SÚKL a ŠÚKL offline, příbalový leták | kapitola 11 |
| B3 | Dávkovač: díl na kartě Dnes, jedno oznámení na díl, plnění | kapitola 15 |
| B4 | Widgety (Glance) s tlačítkem Vzato a Přeskočit | 3.9 |
| B5 | Fotografie léku, krabičky a letáku (výběr fotky bez oprávnění, fotoaparát přes systémovou aplikaci; skener dokumentů ne, vyžaduje Google Play služby) | 3.7 |
| B6 | Souhrn pro lékaře v PDF, historie množství | 3.4 |
| B7 | Přenos dat iOS ↔ Android záložním souborem (iOS dostane import a export stejného formátu) | nové, změní i iOS |
| B8 | Synchronizace přes Google Drive (appDataFolder): přihlášení Google účtem přes prohlížeč (OAuth, bez Google Play služeb, funguje i z F-Droidu), rozsah jen `drive.appdata`; každé zařízení zapisuje vlastní soubor změn a slučuje soubory ostatních podle pravidel výše; fotky jako samostatné soubory; stav synchronizace a odpojení v Nastavení; synchronizace při spuštění, po změně a periodicky přes WorkManager | 3.10 (CloudKit) |

## 4. Rizika

- **Spolehlivost připomínek** na telefonech s agresivní správou baterie. Hlavní riziko projektu;
  testovat na skutečných zařízeních (Samsung, Xiaomi), dokumentovat v nápovědě.
- **Pravidla Google Play** pro přesné budíky a zdravotní aplikace se mění; ověřit před A4 a A7.
- **Ověření vývojářů pro instalaci mimo Google Play**, které Google zavádí: může se týkat APK
  z GitHubu i F-Droidu; ověřit stav před vydáním.
- **Dvojí údržba** iOS a Android: každá změna chování se zapíše do specifikace a odškrtne
  v obou repozitářích; testovací případy se drží shodné.
- **Synchronizace (B8)** si slučování píše sama, CloudKit to na iOS dělal za nás: nejtěžší část,
  potřebuje testy se dvěma zařízeními a ztrátou spojení. Google aplikaci s přístupem k Drive
  ověřuje (zásady soukromí, video přihlášení); zásady soukromí na vzato.app se doplní.
- **12 testerů na 14 dní** pro Google Play: sehnat včas (rodina, známí, uživatelé TestFlightu
  s Androidem).

## 5. Otevřené otázky

1. ~~Systémová záloha Androidu: zahrnout data, nebo vyloučit?~~ Rozhodnuto 21. 11. 2026:
   **zahrnout**; zásady soukromí to zmíní.
2. ~~Testeři pro uzavřený test Google Play?~~ Rozhodnuto 21. 11. 2026: **rodina a známí
   a veřejná výzva** (vzato.app, GitHub, dev.to; seznam testerů jako Google Group).
3. ~~Vývojářský účet Google Play: osobní, nebo organizace?~~ Rozhodnuto 21. 11. 2026:
   **osobní** (25 USD, ověření dokladů, uzavřený test 12 × 14 dní).
