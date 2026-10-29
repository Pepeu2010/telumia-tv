# Cache de metadata TMDB em memória

Os serviços TMDB mantinham dez mapas permanentes sem limite: sete caches de
respostas do `TmdbMetadataService`, o cache de coleções e os dois sentidos do
mapa de IDs do `TmdbService`. Agora cada mapa usa LRU concorrente com capacidade
própria. Ao atingir o limite, remove a entrada menos recentemente usada. O
conteúdo e os retornos dos métodos não mudam; uma entrada removida pode voltar a
ser consultada à rede quando necessária. Os mapas de requisições em andamento
continuam separados, preservando a coalescência existente.

O limite é escolhido na criação do serviço pelo `DeviceCapabilities.resourceTier`
já existente. O tier usa RAM física, classe de memória Android e heap máximo
reportado; `UNKNOWN` adota o perfil conservador de `LOW_RAM`.

| Cache | LOW_RAM / UNKNOWN | BALANCED | HIGH_CAPACITY |
| --- | ---: | ---: | ---: |
| Enrichment | 12 | 24 | 48 |
| Episódios | 8 | 16 | 32 |
| Pessoas | 12 | 24 | 48 |
| Recomendações | 6 | 12 | 24 |
| Cabeçalhos de entidade | 12 | 24 | 48 |
| Rails de entidade | 6 | 12 | 24 |
| Browse de entidade | 6 | 12 | 24 |
| Coleções de filmes | 6 | 12 | 24 |
| Mapeamentos IMDB ↔ TMDB, por direção | 128 | 256 | 512 |

São tetos de quantidade de entradas, não medições em bytes. Cada resposta pode
ter tamanhos diferentes; os limites reduzem retenção sem memória infinita, mas
não provam redução específica de heap, jank ou latência em um aparelho. O tier é
avaliado ao construir o singleton; não há reavaliação durante a sessão neste
incremento. As caches em voo permanecem separadas para preservar a coalescência
de requisições.

## Verificação deste incremento integrado à fonte estável 1.0.5

- 33 testes direcionados passaram: LRU (3), limites por tier (2),
  `TmdbMetadataServiceTest` (27) e eviction dos mapeamentos `TmdbService` (1).
- `:app:assembleFullDebug` gerou os cinco APKs locais com application ID
  `io.github.pepeu2010.telumia.tv.debug`, versão `1.0.5`, código `10015`; não
  foram publicados e não atualizam a instalação de produção.
- Desktop, sem mudança de fonte neste incremento, passou 22 testes focados de
  cache. `:composeApp:packageMsi` terminou com sucesso como `UP-TO-DATE`.
- Fonte: commit [`5ddd99b`](https://github.com/Pepeu2010/telumia-tv/commit/5ddd99b31ca461fb9e20b40b896572a8da7aeb93), [PR #27](https://github.com/Pepeu2010/telumia-tv/pull/27), baseado na correção de quota Coil da fonte estável 1.0.5.
- Não houve medição física de heap, jank, CPU ou latência em TV Box. Build e
  testes locais não demonstram desempenho em hardware real.

O serviço de trailers mantém caches de busca e de fontes YouTube separados; seus limites e validação estão em [TRAILER_CACHE.md](TRAILER_CACHE.md).
