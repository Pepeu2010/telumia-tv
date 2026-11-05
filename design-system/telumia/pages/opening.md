# Abertura

- A marca aparece durante o carregamento real, sem acrescentar espera artificial.
- Movimento completo permite feixe de luz, zoom discreto e deslocamento curto.
- Movimento reduzido usa opacidade; zoom, deslocamento e feixe ficam desativados.
- Movimento desligado mostra a marca sem transicao espacial.
- Um fundo local invalido deve seguir para o carregador de imagem sem derrubar a composicao.
- A abertura deve liberar a tela conforme a politica de startup, incluindo acesso direto a conteudo, perfis e configuracao inicial.

## Verificacao

- StartupSplashMotionTvTest cobre os tres modos de movimento e URI local malformada.
- A existencia da marca nestes testes prova composicao sem crash, mas nao mede fluidez nem a transicao completa ate a Home.
