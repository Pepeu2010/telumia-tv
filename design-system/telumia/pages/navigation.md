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

## Validacao em 2026-11-05

- TelumiaSidebarTvTest: 4 testes instrumentados aprovados no emulador Android TV API 30.
- Controle percorre Inicio, Busca, Biblioteca, TV ao vivo e Configuracoes nos dois sentidos e confirma foco/selecao em cada item.
- Percurso aprovado com movimento completo, reduzido e desligado; beacon aciona a abertura da lateral.
- O teste exercita componentes com rotas controladas. Retorno de filme real e restauracao de rolagem de cada tela ainda precisam de verificacao integrada.
- Captura da TV nao foi recuperada do armazenamento externo apos o teste; nao declarar inspecao visual desta captura.

## Restauracao da Biblioteca

- Corrigido indice da grade: os cartazes sao precedidos por tres itens de cabecalho, ou quatro com acoes de tracking.
- Se o cartaz salvo estiver fora da tela, a grade rola ate o indice correto e aguarda um frame antes de solicitar foco.
- LibraryFocusRestorePolicyTest: 3 testes aprovados.
- LibraryFocusRestoreTvTest: 2 testes instrumentados aprovados, restaurando cartaz 80 fora da tela com/sem acoes de tracking; indice anterior ao foco, visibilidade e foco conferidos.
- O Compose pode reposicionar a linha ao receber foco. Nao exigir coordenada vertical fixa depois desta acomodacao.
- Estes testes usam uma grade controlada com a politica de producao. O ciclo completo de filme real permanece pendente.
