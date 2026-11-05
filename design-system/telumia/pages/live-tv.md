# TV ao vivo

## Fontes por perfil

- Fontes M3U/XMLTV continuam locais ao perfil ativo.
- Corrigida edicao de configuracao ilegivel: a escrita falha preservando o payload existente, em vez de substitui-lo por uma lista vazia.
- Leitura respeita cancelamento da coroutine.
- LiveTvSourceStoreTest e GuideTimelineTest aprovados em 2026-11-05: isolamento entre dois perfis, preservacao em falha e separacao da timeline por sourceId/channelId.

## Pendencias integradas

- Validar com fontes reais importacao, atualizacao, favoritos, recentes e navegacao ate o player.
- Lembretes de programas e equivalencia visual com Desktop ainda precisam de conclusao.
