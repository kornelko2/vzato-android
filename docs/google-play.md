# Google Play: stav a postup

Pozastaveno 21. 11. 2026. Tento dokument popisuje, co už je hotové a jak pokračovat, až bude IČO.

## Proč pozastaveno

Aplikace byla dvakrát zamítnuta podle pravidla *Play Console Requirements* („Violation of Play
Console Requirements“, oblast *Developer Account*). Od 31. 8. 2024 smí zdravotnické aplikace
vydávat jen **účet organizace**. Vzato v deklaraci zdravotních aplikací uvádí *Medication and
Treatment Management*. Google tuto funkci řadí do skupiny *Medical* a v jejím popisu výslovně
jmenuje připomínky léků
([Health app categories](https://support.google.com/googleplay/android-developer/answer/13996367),
[Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291)).

Co nepomohlo nebo nemá smysl:
- Kategorie změněná z Lékařství na Zdraví a fitness: zamítnuto znovu.
- **Měnit deklaraci nebudeme.** Odpovídá skutečnosti a nepravdivá deklarace ohrožuje celý účet.
- Odvolání: Google rozhodl podle vlastních pravidel správně.

## Co už je hotové (osobní účet)

- Aplikace **Vzato – léky včas**, balíček `app.vzato`, výchozí jazyk čeština.
- Interní test: verze 1 (1.0), seznam testerů „kornel a bernard“.
- Uzavřený test *Alpha*: verze 1 (1.0), Česko a Slovensko, stejný seznam testerů (zamítnuto).
- Záznam v obchodu ve čtyřech jazycích, včetně textu, že aplikace není zdravotnický prostředek.
  Poslední verze je nahraná, ale neodeslaná ke kontrole.
- Obsah aplikace:
  - Věkové hodnocení.
  - Cílový věk 13–15, 16–17 a 18+.
  - Bezpečnost dat: žádná data se nesbírají.
  - Zdravotní aplikace: Medikace a správa léčby.
  - Bez reklam a bez reklamního ID.
  - Kategorie Zdraví a fitness.
- Služební účet pro API `vzato-publisher@vzato-play-9f8b04.iam.gserviceaccount.com`. Klíč je
  v `~/.android-keys/play-service-account.json`.
- Podpisový klíč a hesla jsou v `~/.android-keys/`, mimo git. Play App Signing je zapnutý.

## Postup, až bude IČO

1. **D-U-N-S číslo.** Je zdarma. Žádá se přes Play Console při zakládání účtu organizace nebo na
   dnb.com a vyřízení trvá dny až týdny. Název a adresa musí přesně odpovídat záznamu
   v živnostenském nebo obchodním rejstříku. Stejné údaje se pak zadávají v Play Console.
2. **Účet vývojáře typu organizace.** Zakládá se nový účet, stávající osobní účet typ změnit
   nemůže. Stojí 25 USD, ověřuje se organizace a kontaktní údaje a jako kontakt slouží
   developer@marazfamily.eu. Do obchodu se pak zobrazí název firmy a adresa podnikání.
3. **Převod aplikace** `app.vzato` z osobního účtu do účtu organizace
   ([Transfer apps](https://support.google.com/googleplay/android-developer/answer/6230247)).
   Novou aplikaci pod stejným balíčkem založit nejde, název balíčku zůstává vázaný na první účet.
   S aplikací se převedou záznam v obchodu, verze i podpisový klíč Play App Signing.
4. **Služební účet** pozvat do nového účtu (Uživatelé a oprávnění) se stejnými oprávněními jako
   předtím. Pak ověřit spojení příkazem `python3 scripts/play.py check`.
5. **Zkontrolovat Obsah aplikace a Přehled publikování.** Po převodu může Google některé
   formuláře chtít znovu.
6. **Vydání:**
   - Účty organizace nemají povinný uzavřený test s 12 testery po 14 dní, který platí pro nové
     osobní účty. Je ale dobré ho stejně krátce udělat s rodinou.
   - Postup: nový build, nahrání, pak `promote` mezi stopami a odeslání ke kontrole v Přehledu
     publikování.

```bash
./gradlew :app:bundleRelease
python3 scripts/play.py listing --no-review     # texty a obrázky z fastlane/metadata
python3 scripts/play.py upload internal         # nebo alpha / production
python3 scripts/play.py promote internal production --no-review
```

`--no-review` uloží změny, ale ke kontrole je pošle až tlačítko v Play Console. Bez něj API
odmítne commit, pokud má aplikace zamítnuté nebo neodeslané změny.

## Mezitím

Android verze se šíří mimo Google Play:
- **GitHub Release:** podepsané APK (`./gradlew :app:assembleRelease`).
- **F-Droid:** nepotřebuje účet ani IČO.

APK z Google Play a z GitHubu nebo F-Droidu mají různé podpisy, takže se navzájem neaktualizují.
