# Biblioteca para TV

## Intencao visual

- Um painel cinematografico unico apresenta titulo e origem ativa, evitando barras concorrentes no topo.
- O marcador lateral e a pilula de origem usam `Primary`; fundo, borda e textos usam tokens semanticos do tema.
- O painel tem altura minima de 64 dp e permanece dentro da area segura de TV.

## Navegacao por controle

- A ordem de foco segue: modo da biblioteca, atualizar quando aplicavel, filtros e cartazes.
- Todo alvo interativo deve preservar pelo menos 48 dp e ter estado de foco explicito.
- Ao voltar da tela de detalhes, o ultimo cartaz recupera o foco.

## Estados e desempenho

- Salvos e nuvem compartilham a mesma hierarquia visual e identificam claramente a origem.
- Carregamento, vazio, erro e sincronizacao nao removem o contexto nem prendem o foco.
- Grades permanecem virtualizadas e o foco nao deve disparar recarregamento de imagens.
- Os modos de movimento completo, reduzido e desligado preservam a mesma geometria.
