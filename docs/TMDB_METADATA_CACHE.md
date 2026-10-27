# Cache de metadata TMDB em memória

`TmdbMetadataService` mantinha sete mapas de respostas sem limite de entradas,
com vida útil igual à do processo. A TV agora usa LRU concorrente e limita cada
mapa separadamente. Ao atingir a capacidade, remove o item menos recentemente
usado; as respostas retornadas pelos métodos e a coalescência de pedidos em
andamento continuam iguais. Uma resposta removida pode ser consultada à rede
novamente quando for pedida.

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

São tetos de quantidade de entradas, não medições em bytes. Cada resposta pode
ter tamanhos diferentes; os limites reduzem retenção sem memória infinita, mas
não provam redução específica de heap, jank ou latência em um aparelho. O tier é
avaliado ao construir o singleton; não há reavaliação durante a sessão neste
incremento. As caches em voo permanecem separadas para preservar a coalescência
de requisições.

## Verificação deste incremento

- 31 testes direcionados passaram: LRU/concorrência, limites por tier e todos os
  26 testes de `TmdbMetadataServiceTest`.
- Suíte completa: 2.058 testes, 13 falhas e 1 skip. As 13 falhas foram
  reproduzidas nas mesmas 10 classes no commit-base `e1e7d992`; nenhuma ocorreu
  nos testes novos ou no serviço TMDB. O registro principal lista as classes.
- APK Full Debug compilado em todas as cinco arquiteturas. O application ID é
  `io.github.pepeu2010.telumia.tv.debug`; os APKs são só para validação.
- O Desktop, sem mudança de fonte, passou novamente os testes focados de cache e
  o `packageMsi`; as tarefas de compilação/empacotamento estavam atualizadas.
- Não houve medição física de heap, jank, CPU ou latência em TV Box. Build e
  testes locais não demonstram desempenho em hardware real.
