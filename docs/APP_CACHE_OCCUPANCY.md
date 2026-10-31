# Ocupação dos caches internos da TV

O modo Auto calcula o espaço reservado para cache regenerável a partir do
armazenamento interno disponível e dos bytes de cache que já existem. A medição
é somente leitura, roda no monitor de ocupação em segundo plano e usa caminhos
allowlisted para não misturar cache com preferências e outros dados duráveis.

O `AddonRepositoryImpl` mantém o último snapshot de manifests instalados em
`shared_prefs/addon_manifest_cache.xml`. Ele carrega esse snapshot no início do
processo e o usa como fallback quando uma atualização de addon falha, incluindo
quando o aparelho está offline. O arquivo e o backup temporário usado na gravação
atômica (`addon_manifest_cache.xml.bak`) ficam fora de `cacheDir`, `filesDir` e
`codeCacheDir`; antes desta alteração, esses bytes não entravam na ocupação Auto.

Agora o monitor mede somente esses dois arquivos pelo tamanho e atributos do
sistema de arquivos. Não lê nem altera o conteúdo e ignora os demais XML de
`shared_prefs`. A leitura usa o diretório privado do aplicativo; nenhuma permissão
de armazenamento é solicitada. Leituras offline e o ciclo de carregamento e
persistência dos manifests permanecem iguais.

## Limites da verificação

O teste JVM cobre a inclusão exata do arquivo e do backup sem modificar o snapshot
ou contar preferências não relacionadas. Ele não executa um ciclo de login/addon
offline em um dispositivo físico. A medição também não define uma quota própria
para o snapshot: ela corrige apenas a ocupação usada pelo Auto.
