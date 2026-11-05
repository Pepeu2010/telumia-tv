# Navegacao para TV

## Regra de chrome unico

- A barra lateral e o beacon pertencem ao mesmo sistema de navegacao global.
- Titulos internos identificam o destino e nao devem ser confundidos com uma segunda navegacao.
- Quando o beacon ocupa o espaco do titulo, a Biblioteca preserva 64 dp de respiro sem desenhar um painel vazio ou expor textos invisiveis a acessibilidade.

## Foco e retorno

- Esquerda a partir do primeiro item de conteudo abre a lateral.
- Voltar primeiro fecha a lateral e devolve o foco ao conteudo; uma nova acao de voltar segue a hierarquia da tela.
- A rota selecionada permanece evidente nos estados expandido e recolhido.

## Movimento

- Expandir e recolher usa os tokens de movimento do tema.
- Movimento reduzido ou desligado nao altera a ordem de foco nem a disponibilidade das rotas.
