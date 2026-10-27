# Cache local de avaliações MDBList

As avaliações do MDBList são dados regeneráveis. O cache TV agora grava em
`cacheDir/metadata/mdblist/ratings_v1.json` e consome a categoria `METADATA` da
política central de cache. O arquivo tem teto adicional de 8 MiB, tempo de vida
de 12 horas e conserva primeiro os registros mais recentes quando a quota fica
menor. A leitura e gravação acontecem no dispatcher de IO; o estado em memória
é limitado a 25 mil entradas.

Uma instalação que ainda tenha `filesDir/mdblist_cache/ratings_v1.json` lê esse
arquivo uma vez e remove a cópia durável antiga. O payload é cache, não
configuração do usuário; credenciais MDBList não são gravadas nesse arquivo.
Com escrita desativada por falta de espaço, as entradas existentes continuam
disponíveis para leitura e novas avaliações permanecem apenas em memória.

O snapshot de ocupação agora conta o diretório `metadata` no orçamento Auto.
O cache permanece descartável: o sistema Android pode removê-lo quando precisar
de espaço e a ausência dele não impede novas consultas MDBList.

## Verificação

- 5 testes de `MdbListRatingsDiskCacheTest` e 11 de `MdbListRatingsLoaderTest`
  passaram no conjunto `:app:testFullDebugUnitTest`.
- `:app:compileFullDebugAndroidTestKotlin` e `:app:assembleFullDebug` passaram;
  foram gerados APK universal, ARM64, ARMv7, x86_64 e x86.
- Desktop não teve alterações de código nesta entrega; os testes de cache,
  `desktopJar` e `packageMsi` foram executados como gate cruzado.
- Não houve medição em TV Box física nem comparação de desempenho no aparelho.
  Os APKs produzidos são Full Debug (`.debug`), não atualizações para a versão
  instalada de produção.
