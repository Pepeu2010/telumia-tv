# Cache de recomendações Trakt e Simkl

`TraktRelatedService` e `SimklRelatedService` usam a mesma `TtlLruCache`, implementada sobre o `BoundedLruCache` de ordem de acesso. Cada serviço mantém seu próprio cache. O TTL continua em 10 minutos, as chaves e a ordenação de resultados foram preservadas, listas vazias continuam sendo valores cacheáveis e `forceRefresh` invalida a entrada antes de consultar a API. A remoção de uma entrada expirada é condicional para não apagar um valor escrito por uma atualização concorrente.

## Orçamento inicial por aparelho

| Tier de memória | Entradas por serviço |
| --- | ---: |
| LOW_RAM | 4 |
| UNKNOWN | 4 |
| BALANCED | 8 |
| HIGH_CAPACITY | 16 |

São limites iniciais de quantidade de entradas, não uma medição exata de bytes de heap. O tier é lido quando o singleton do serviço é criado. Dispositivos desconhecidos recebem o orçamento conservador. A Fase 1-D ainda exige auditoria dos consumidores restantes, fluxos offline completos e validação de desempenho em hardware real.

## Validação local

No commit base `6224107be17ed15db4b9a030c5409839b6dc7f3c`, os testes novos foram executados primeiro e falharam por ausência de `TtlLruCache` e `RelatedServiceCacheLimits`, como esperado. Depois da implementação, passaram 9 testes: LRU, expiração, cache de listas vazias, remoção explícita, preservação de atualização concorrente, validação de parâmetros e budgets por tier.

O comando ampliado `:app:testFullDebugUnitTest --tests '*Trakt*Test' --tests '*Simkl*Test' --tests '*TtlLruCacheTest' --tests '*RelatedServiceCacheLimitsTest'` executou 185 testes e encontrou uma falha em `TraktAuthServiceTest`. A mesma classe e a mesma asserção falharam no worktree limpo do commit base `6224107`; trata-se de uma falha preexistente no mock da renovação do token, não relacionada aos caches. Os 9 testes deste incremento passaram.

`:app:assembleFullDebug` compilou as cinco variantes locais com versionName `1.0.7`, versionCode `10017` e application ID `.debug`. Elas são artefatos locais de validação, com assinatura debug; o workflow oficial da release gera APKs assinados pela chave persistente para instalação sobre a versão estável.

| Variante | Bytes | SHA-256 |
| --- | ---: | --- |
| Universal | 180027473 | `135558f68e016154c3f30d184647719b50f77fe30fcf3f532c42eccfcb55603d` |
| ARM64-v8a | 93056235 | `ead43fc328e2bacff97c2b8fc27bdfedb9147b7a38f5c62b4d2463adc72eca10` |
| Armeabi-v7a | 90954649 | `02c34b3beba3c54909d1966a5af73ddaf34be9cfb516dff93d917aef7d492371` |
| x86_64 | 95476750 | `46e72b6ebcf5c3ca8965fe5332df953ef56488c4a92f03596e960a2a28239b39` |
| x86 | 94636885 | `3b001761a5a2813738a0dc012320e5d935f9ac848109c77aa1c65886313d7ea2` |

O teste local de build MSI oficial no workflow Desktop `40981827939` parou em `Configure desktop runtime` porque faltam `LOCAL_PROPERTIES_BASE64`, `SENTRY_AUTH_TOKEN` e `SENTRY_DESKTOP_DSN` nos segredos do repositório. Como não houve alteração no cliente Desktop, o pacote Windows permanece o MSI estável 1.0.4 já publicado; o workflow combinado verifica e inclui esse pacote sem recompilá-lo.

Nenhum teste físico em PC ou TV Box, medição de heap/jank ou fluxo offline completo foi feito neste incremento.
