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

Após o bump estável, `:app:assembleFullDebug` compilou as cinco variantes locais com versionName `1.0.8`, versionCode `10018` e application ID `.debug`. O `aapt` confirmou a versão e `apksigner` confirmou a assinatura local (certificado debug SHA-256 `da24b46124823f776e120a84a91923123da1fd3f21d94e1eb06f4d1a76471805`). Esses APKs são artefatos de validação; o workflow oficial da release assina os APKs distribuídos com a chave persistente para atualização sobre a versão estável.

| Variante | Bytes | SHA-256 |
| --- | ---: | --- |
| Universal | 180028273 | `2071c89af844a11a3d33be4fbd2066ca11b80e6e2c747a25a8b74f92527e37bc` |
| ARM64-v8a | 93057035 | `830a94cb14581a5c92c49c07b81d1ad348d7baef18d0e84e31b80cdb1711ccb0` |
| Armeabi-v7a | 90955445 | `9d3d21cafd6509378b8e00cfdb1a23f61394f0dcfd5e574eaf42e9e2127b1886` |
| x86_64 | 95477550 | `18a49b929a120f1da2130eaddd238d9d3d11d4f92e2811d3c5324c8ae4340757` |
| x86 | 94637685 | `7e606add6382b05bf9fbf8ef998247f8a41cb4ef0e27ab582442423b49959891` |

O teste local de build MSI oficial no workflow Desktop `40981827939` parou em `Configure desktop runtime` porque faltam `LOCAL_PROPERTIES_BASE64`, `SENTRY_AUTH_TOKEN` e `SENTRY_DESKTOP_DSN` nos segredos do repositório. Como não houve alteração no cliente Desktop, o pacote Windows permanece o MSI estável 1.0.4 já publicado; o workflow combinado verifica e inclui esse pacote sem recompilá-lo.

Nenhum teste físico em PC ou TV Box, medição de heap/jank ou fluxo offline completo foi feito neste incremento.
