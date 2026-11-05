# Busca

## Objetivo

A busca deve revelar conteúdo, produtoras e emissoras com a mesma hierarquia visual,
sem transformar entidades em botões genéricos ou misturá-las silenciosamente aos pôsteres.

## Produtoras e emissoras

- Produtoras e emissoras aparecem em seções distintas, cada uma com marcador de acento.
- Cada entidade usa cartão compacto com ícone, nome em uma linha e área de toque/foco adequada.
- Na TV, o foco usa a cor e o anel semântico do tema e mantém pelo menos 48 dp de altura.
- No PC, o cartão preserva borda, superfície elevada e resposta nativa a mouse e teclado.
- Selecionar uma entidade abre seu catálogo; não executa uma busca textual aproximada.

## Resultados e estados

- Resultados de addons, produtoras e emissoras podem coexistir sem alterar a ordem de origem.
- Carregamento, ausência de resultados e falha de rede permanecem explícitos.
- A busca brasileira prioriza correspondências localizadas, mas não altera IDs ou inventa
  temporadas e episódios.
- Histórico e consulta atual são sempre escopados ao perfil ativo.

## Movimento e desempenho

- Entrada e troca de resultados usam somente opacidade e transformação.
- Movimento reduzido mantém a mesma geometria e foco.
- Diretórios locais de emissoras são consultados fora da thread principal.

