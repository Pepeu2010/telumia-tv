# Caches em memória do serviço de trailers

`TrailerService` mantém dois caches de longa duração: resultados de busca por título (inclusive o sentinel de ausência) e fontes de reprodução associadas a IDs de vídeos do YouTube. Ambos usam `BoundedLruCache`, com LRU sincronizado e capacidade por tier do `DeviceCapabilities`:

| Cache | LOW_RAM / UNKNOWN | BALANCED | HIGH_CAPACITY |
| --- | ---: | ---: | ---: |
| Resultados de busca | 12 | 24 | 48 |
| Fontes de reprodução YouTube | 6 | 12 | 24 |

O cache YouTube continua armazenando somente fontes resolvidas com sucesso. A validade segue a expiração embutida no URL quando disponível e, caso contrário, o TTL existente de três horas. Ao exceder a capacidade, a entrada menos recentemente usada é removida e pode ser resolvida novamente. O limite é por quantidade de entradas, não por bytes; o tier é lido ao construir o serviço.

`BoundedLruCache.remove(key, expectedValue)` remove uma entrada expirada apenas se ela ainda corresponder ao objeto lido, evitando apagar uma atualização concorrente mais recente.

## Verificação

Na branch `codex/1d-tv-bounded-trailer-cache`, commit `0b28e22da`, PR [#29](https://github.com/Pepeu2010/telumia-tv/pull/29), passaram 19 testes JVM: `BoundedLruCacheTest` (4), `TrailerCacheLimitsTest` (2), `TrailerServiceBoundedCacheTest` (2), `TrailerServiceCancellationTest` (2), `TrailerServiceCandidateRankingTest` (5), `TrailerServiceUseTrailersGateTest` (2) e `TrailerServiceYouTubeSessionCacheTest` (2). O build `:app:assembleFullDebug` concluiu em 7m40s e gerou as cinco variantes locais `1.0.6`/`10016`, application ID `.debug`. São artefatos de validação com assinatura debug, não publicados e não instaláveis por cima do APK de produção.

No Desktop, `desktopTest` e `packageMsi` passaram como `UP-TO-DATE`, pois não houve alteração naquele cliente. Não houve medição de heap, latência ou jank em hardware físico, nem QA em TV Box. Os limites não representam orçamento exato em bytes.
