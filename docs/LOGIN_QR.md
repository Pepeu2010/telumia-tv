# Login por QR na TV

O cliente prefere `verification_uri_complete` quando o backend a envia; se o campo estiver ausente ou inválido, usa `verification_uri` e depois a URL de login configurada. O código manual continua visível para fluxos em que a URL base precisa de entrada do usuário.

O bitmap é rasterizado com pixels pretos/brancos sem antialias, com quiet zone mínima de quatro módulos. A geração roda em `Dispatchers.Default`, evitando dezenas de milhares de operações de desenho na thread principal. O formato RGB_565 reduz o tamanho do bitmap opaco.

Validação: seis testes JVM passaram, incluindo decodificação da matriz e fallback de URL; `:app:assembleFullDebug` compilou os cinco APKs locais v1.0.11/code10022; `:app:assembleFullDebugAndroidTest` compilou o teste que decodifica o bitmap Android. Este host não tem emulador ou dispositivo ADB, portanto o teste instrumentado não foi executado. O endpoint oficial foi consultado e retornou uma sessão válida com código e URL completa; os valores do código não foram registrados.

Os APKs locais são Full Debug de validação, ID `.debug`, com assinatura de desenvolvimento. A publicação estável usa a chave persistente do workflow oficial.

## Configuração do backend

O build aceita `NUVIO_SUPABASE_URL` e `NUVIO_SUPABASE_ANON_KEY` como nomes principais, além dos nomes antigos `SUPABASE_URL` e `SUPABASE_ANON_KEY`. Sem URL ou chave, o login por QR é interrompido antes da requisição e a tela explica que a configuração está ausente. Isso evita deixar a tela em estado de geração quando um APK de desenvolvimento foi compilado sem configuração do backend.
