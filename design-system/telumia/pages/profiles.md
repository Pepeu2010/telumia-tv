# Perfis para TV

## Direcao visual

- O perfil focado atualiza fundo, tema e marca sem interromper a navegacao.
- Cartoes usam `BackgroundCard`, `BackgroundElevated`, `Border` e `Primary`; temas personalizados nunca dependem de uma paleta fixa.
- Avatar, PIN e perfil principal permanecem identificaveis a distancia.

## Navegacao por controle

- O foco inicial retorna ao perfil ativo ou ao ultimo perfil destacado.
- Pressionar seleciona; pressionar e segurar abre as opcoes sem tambem selecionar.
- A geometria do grid permanece estavel quando o foco cresce visualmente.

## Robustez

- Falha de avatar preserva inicial e cor.
- Os modos de movimento completo, reduzido e desligado mantem a mesma ordem de foco.
- Layout compacto preserva legibilidade em resolucoes e alturas menores.
