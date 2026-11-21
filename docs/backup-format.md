# Backup file

`Settings › Backup › Save a backup` writes one JSON file; `Restore from a backup` replaces all
data with one. The format is meant to be read by the iOS app later (plan phase B7), so it uses the
names of the iOS Core Data model (version 8).

```json
{
  "format": "vzato-backup",
  "version": 1,
  "app": "android 0.1",
  "exportedAt": 1795264620000,
  "profiles": [ { "id": "…", "name": "Me", "kindRaw": "person", "isDefault": true, … } ],
  "medications": [ { "id": "…", "profileId": "…", "name": "Letrox", "unitsPerDose": 0.5, "unitKindRaw": "tablet", … } ],
  "schedules": [ { "id": "…", "medicationId": "…", "weekdaysRaw": "1,2,3,4,5,6,7", "minutesRaw": "480", … } ],
  "doseEvents": [ { "id": "…", "medicationId": "…", "scheduledAt": 1795240800000, "statusRaw": "taken", "units": 0.5, … } ],
  "activity": [ { "id": "…", "date": 1795241100000, "kindRaw": "taken", "medicationName": "Letrox", … } ]
}
```

- Moments are epoch milliseconds (UTC), UUIDs strings, enums their raw strings (`taken`,
  `before`, `months`…), lists comma-separated as in the database (`weekdaysRaw` uses iOS weekday
  numbers, 1 = Sunday).
- A missing field takes the database default; a field that is `null` is left out.
- Readers refuse a file whose `format` differs or whose `version` is newer than they know.
- Photos are not in version 1 (photos arrive in phase B5).
