# Arquitectura — Time (usagestats)

> Este documento todavía no existía en el repositorio. Se crea aquí con las
> dos secciones que pide [TIM-23](https://linear.app/timeappdevelopment/issue/TIM-23/redisenar-el-motor-de-mood-del-avatar-sobre-state-snapshots-sustituye)
> (§7.2 y §8.3). El resto de secciones (1–6, 8.1, 8.2, 9+) no se han escrito
> todavía — se irán añadiendo a medida que existan las piezas que describen.

## §7.2 — Esquema de datos: uso diario y mood

La persistencia de uso vive en Room (`usagestats.db`, `data/db/AppDatabase.kt`) y se reparte en dos frentes independientes que **no se solapan**:

### `usage_daily` / `apps` — estadísticas agregadas del día

Siguen alimentando la pantalla de estadísticas de siempre (desbloqueos y
tiempo de uso, hoy y media de 7 días — ver §8.2 de esta misma app, pendiente
de escribir). Una fila por `(packageName, date)`, actualizada por
`UsageStatsCalculator.syncToday()` cada vez que se abre la home. **No** son
la fuente del mood.

### `app_sessions` / `state_snapshots` — motor de mood (TIM-23)

Sustituyen por completo el diseño de `daily_features`/`avatar_state` que
TIM-9 (cerrado 18 sept, ver comentario de superación en el propio issue)
había fijado. `usage_daily` no se toca: sigue sirviendo a la pantalla de
estadísticas de siempre; estas dos tablas son exclusivas del mood.

```sql
CREATE TABLE app_sessions (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  packageName  TEXT NOT NULL,
  date         TEXT NOT NULL,
  startedAt    INTEGER NOT NULL,
  endedAt      INTEGER,          -- NULL si sigue abierta
  durationMs   INTEGER
);
CREATE INDEX idx_app_sessions_date ON app_sessions(date);
CREATE INDEX idx_app_sessions_package_date ON app_sessions(packageName, date);

CREATE TABLE state_snapshots (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  capturedAt            INTEGER NOT NULL,  -- instante LÓGICO que describe la fila
  writtenAt             INTEGER NOT NULL,  -- instante REAL en que se escribió
  date                  TEXT NOT NULL,
  trigger               TEXT NOT NULL,     -- 'tick_30min' | 'unlock'
  cumScreenTimeMs       INTEGER NOT NULL,
  cumUnlocksCount       INTEGER NOT NULL,
  dailyGoalMs           INTEGER NOT NULL,  -- copia informativa, ya no es el denominador de screen_ratio
  avg7dScreenTimeMs     REAL NOT NULL,     -- media de la MISMA franja de 30min, últimos 7 días
  goalScreenTimeMs      REAL NOT NULL,     -- avg7dScreenTimeMs × 0.9
  avg7dUnlocks          REAL NOT NULL,
  goalUnlocks           REAL NOT NULL,
  screenRatio           REAL NOT NULL,
  unlockRatio           REAL NOT NULL,
  idleMs                INTEGER NOT NULL,
  mood                  TEXT,              -- NULL en tick_30min; calculado en unlock
  triggeredBy           TEXT               -- NULL en tick_30min; 'screen'|'unlocks'|'both'|'idle' en unlock
);
CREATE INDEX idx_state_snapshots_date ON state_snapshots(date);
```

Implementación: `data/db/AppSessionEntity.kt`, `AppSessionDao.kt`,
`StateSnapshotEntity.kt`, `StateSnapshotDao.kt`. Sin migraciones todavía —
`AppDatabase` usa `fallbackToDestructiveMigration()` mientras el esquema
sigue moviéndose en esta fase de desarrollo.

`app_sessions` es la base para calcular `cumScreenTimeMs` (tiempo de
pantalla acumulado en el día hasta un instante dado) e `idleMs` (tiempo sin
actividad) con precisión — `queryUsageStats` no da eso directamente, solo
totales por día.

## §8.3 — Motor de mood

Implementación: `data/mood/Mood.kt`, `AppSessionSyncManager.kt`,
`StateSnapshotSyncManager.kt`. Sustituye la fórmula y los 4 estados de
TIM-9 por lo siguiente.

### Estados (5, antes 4)

`contento` · `tranquilo` · `neutral` · `inquieto` · `agotado`

### Fórmula

```
screen_ratio    = cumScreenTimeMs / max(goalScreenTimeMs, 1)
unlock_ratio    = cumUnlocksCount / max(goalUnlocks, 0.5)
combined_ratio  = (screen_ratio + unlock_ratio) / 2       -- media, no max (TIM-9 usaba max)

goalScreenTimeMs = avg7dScreenTimeMs × 0.9   -- "bate tu propia media", no un objetivo fijo
goalUnlocks      = avg7dUnlocks × 0.9

ratio_mood =
  combined_ratio < 0.35 → contento
  combined_ratio < 0.7  → tranquilo
  combined_ratio < 1.0  → neutral
  combined_ratio < 1.5  → inquieto
  combined_ratio ≥ 1.5  → agotado
```

`avg7dScreenTimeMs`/`avg7dUnlocks` comparan contra la **misma franja horaria
de 30 minutos**, promediada en los últimos 7 días — no contra la media del
día completo. Ver `StateSnapshotSyncManager.averageForSameSlot()`.

### Suelo por inactividad

Se aplica después de `ratio_mood` y solo puede mejorar el resultado:

| `idleMs` | Suelo |
| -- | -- |
| ≥ 4 h | nunca peor que `contento` |
| ≥ 1 h | nunca peor que `tranquilo` |
| < 1 h | sin suelo, manda `ratio_mood` |

`mood` final = el mejor de `ratio_mood` y el mood del suelo.
`triggered_by = 'idle'` cuando el suelo cambia el resultado; si no, `'both'`
si `|screen_ratio − unlock_ratio| < 0.1`, si no la señal mayor
(`'screen'`/`'unlocks'`).

`idleMs` excluye estrictamente la sesión que el propio evento de
desbloqueo acaba de abrir (con `<=` en vez de `<` estricto sale siempre 0
en la única fila que el usuario ve — bug real del prototipo, corregido en
`StateSnapshotSyncManager.computeIdleMs()`).

### Escritura sin timers

No hay `WorkManager`/`AlarmManager`. En cada wake (`StateSnapshotSyncManager.onWake`,
llamado desde la home igual que `UsageStatsCalculator.syncToday`):

1. Sincroniza `app_sessions` desde `UsageEvents` (`AppSessionSyncManager.sync`).
2. Reconstruye retroactivamente los cortes de 30 min cruzados desde el
   último `state_snapshots` guardado (`trigger='tick_30min'`, `mood` queda
   `NULL` — nadie ve esas filas).
3. Inserta una fila final `trigger='unlock'` en vivo, con `mood`/`triggeredBy`
   calculados — la única que el usuario ve.
4. Todo en una sola transacción (`StateSnapshotDao.insertAll`).

Límite de seguridad: como mucho 336 ticks de backfill por wake (~7 días) si
la app lleva mucho tiempo sin abrirse.

### Pendiente de decidir (no bloquea esta fase)

- Umbrales (0.35/0.7/1.0/1.5): se han dejado igual que TIM-9 a propósito.
  Al ser ahora una media (más "amable" que el máximo anterior), podrían
  revisarse con datos reales.
- Autocomparación (×0.9 sobre tu propia media) vs. objetivo fijo
  configurable: cambio de filosofía de producto, confirmar antes de pulir
  la UI del mood.
- Histéresis (2 confirmaciones para empeorar, exigida por TIM-9): no
  implementada — cada wake recalcula en frío. Decidir si hace falta con el
  suelo por inactividad ya en su sitio.
- Arranque en frío: durante la primera semana de uso, `averageForSameSlot`
  puede no tener 7 días de histórico en la franja horaria en cuestión y
  devuelve 0, lo que puede distorsionar el mood inicial. No resuelto.

### Fuera de alcance en esta fase

Este motor solo calcula y persiste el mood como dato (`state_snapshots.mood`).
No hay avatar visual, ni Rive, ni ningún cambio de UI más allá de una línea
de texto de depuración en la pantalla de estadísticas
(`StatsDetailScreen`) mostrando el último mood calculado.
