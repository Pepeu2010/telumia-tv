# Login por QR na TV

O cliente prefere `verification_uri_complete` quando o backend a envia; se o campo estiver ausente ou inválido, usa `verification_uri` e depois a URL de login configurada. O código manual continua visível para fluxos em que a URL base precisa de entrada do usuário.

O bitmap é rasterizado com pixels pretos/brancos sem antialias, com quiet zone mínima de quatro módulos. A geração roda em `Dispatchers.Default`, evitando dezenas de milhares de operações de desenho na thread principal. O formato RGB_565 reduz o tamanho do bitmap opaco.

Validação em 2026-10-30: os seis testes JVM focados em QR passaram e `:app:assembleFullDebug` compilou os APKs v1.0.12/code10024. O BuildConfig gerado incluiu URL do backend e chave anon configurada. No MXQPRO conectado por ADB, o Telumia instalado (`io.github.pepeu2010.telumia.tv.debug` v1.0.10) registrou `anonKeyConfigured=false` e falhou ao iniciar a RPC antes de gerar o QR. O pacote `io.github.pepeu2010.telumia.box` é apenas o launcher. A instalação do APK corrigido sobre o `.debug` foi recusada porque a assinatura antiga não corresponde à chave de desenvolvimento atual; por isso, a correção compilada ainda não foi validada em execução no aparelho.

Os APKs locais são Full Debug de validação, ID `.debug`, com assinatura de desenvolvimento. A publicação estável usa a chave persistente do workflow oficial.

## Configuração do backend

O build aceita `NUVIO_SUPABASE_URL` e `NUVIO_SUPABASE_ANON_KEY` como nomes principais, além dos nomes antigos `SUPABASE_URL` e `SUPABASE_ANON_KEY`. Sem URL ou chave, o login por QR é interrompido antes da requisição e a tela explica que a configuração está ausente. Isso evita deixar a tela em estado de geração quando um APK de desenvolvimento foi compilado sem configuração do backend.

O diagnóstico da release estável v1.0.11 confirmou que ela usa o commit TV `4afb3243e83e42fd8423f2398a256da0ee523cdc`. O workflow principal que a publicou configurou a assinatura, mas não forneceu propriedades Supabase ao build. Como `local.properties` não faz parte do checkout, os campos `SUPABASE_URL` e `SUPABASE_ANON_KEY` ficaram vazios e o app parou antes da chamada RPC. A release alpha posterior continha o código corrigido, mas não substitui a atualização estável.

O pipeline principal atual do repositório `Pepeu2010/telumia` injeta `NUVIO_SUPABASE_URL` e `NUVIO_SUPABASE_ANON_KEY` dos segredos de produção e valida ambos antes de montar APKs estáveis. O workflow deste repositório também valida as propriedades em `local.properties`. Em qualquer caso, uma configuração ausente deve interromper a publicação em vez de gerar um APK estável sem login.

Verificação local em 2026-10-30: os testes JVM do seletor de releases e dos payloads QR passaram; `:app:assembleFullDebug` e `:app:assemblePlaystoreDebug` compilaram; `:app:connectedFullDebugAndroidTest` executou `QrCodeGeneratorAndroidTest` no AVD Android TV e confirmou que o bitmap gerado pode ser decodificado para o link de login. O teste não faz uma aprovação de conta real. O build local não contém credenciais de produção; o próximo pacote estável será validado pelo workflow oficial.
