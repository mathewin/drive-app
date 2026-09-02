# DriveWin Calculadora de Ganhos

App Android nativo (Kotlin + Jetpack Compose) que le **automaticamente** as ofertas de corrida da **Uber** e **99** via acessibilidade e mostra, em um card flutuante arrastavel, a analise financeira instantanea: R\$/km, R\$/hora, classificacao e nota de 0 a 100 contra as metas do motorista.

O app faz **somente uma coisa**: oferta na tela -> detecta -> le -> valida -> calcula -> mostra o card. Sem ranking, sem historico complexo, sem gamificacao, sem rede social.

## Fluxo

```
IDLE -> DETECTING -> READING -> VALIDATING -> CALCULATING -> DISPLAYING -> aguarda nova oferta
```

- Detecta apenas os pacotes `com.ubercab` e `br.com.taxiapp`.
- Eventos: `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_TEXT_CHANGED`.
- Debounce de 150 ms, hash anti-duplicacao (app + valor + distancia + tempo) evita recalcular a mesma oferta.
- Duas leituras com pequeno intervalo confirmam os dados; leituras divergentes sao relidas.
- Parsers separados: `UberParser` e `NinetyNineParser`.
- Fallback via **ML Kit OCR** (com captura de tela autorizada) apenas quando a leitura direta falhar; nunca roda continuamente.

## Calculos

- Distancia operacional = ate o passageiro + viagem (ou distancia total disponivel).
- Tempo total = ate o passageiro + viagem (ou tempo total disponivel).
- `R$/km = valor / distancia total`; `R$/hora = valor / tempo total * 60`.

## Classificacao e nota

- Excelente: ambas as metas superadas com margem >= 50%.
- Boa: ambas as metas atingidas.
- Media: apenas uma meta atingida.
- Ruim: nenhuma meta atingida.
- Nota 0-100: 50% desempenho R\$/km + 50% R\$/h, comparado com as metas, limite 100.

## Preparo para teste de rua

1. Abra no Android Studio (Run / Build APK) e instale.
2. Aba **Leitura**: conceda Acessibilidade, Sobreposicao e isente a Bateria (essencial no Xiaomi/MIUI).
3. No Xiaomi/MIUI libere tambem o **autostart** do DriveWin (Seguranca -> Apps -> Iniciar automaticamente).
4. Toque em **INICIAR MONITORAMENTO** (ativa o servico em primeiro plano com notificacao).
5. Aba **Metas**: ajuste metas de R\$/km e R\$/h, aparencia do card e ative o OCR (autorizando a captura de tela) se quiser o fallback.
6. Abra a Uber ou a 99 e aguarde uma oferta. O card aparece sozinho, com bip e vibracao.

O card compacto mostra `DRIVEWIN`, classificacao colorida, R\$/km, R\$/h e nota. Toque para expandir (valor, distancia, tempo), arraste para mover (a posicao e lembrada).

## Depuracao na rua

```
adb logcat -s DriveWin
```

Mostra `service connected`, `offer ... rkm=... rh=... nota=...`, `direct read falhou, tentando OCR`, `ocr ok ...` e `ocr fail`.

## Estrutura

- `CalculatorService.kt` - servico de acessibilidade com maquina de estados
- `UberParser.kt` / `NinetyNineParser.kt` - parsers separados por app
- `ParsingUtils.kt` - normalizacao de dinheiro, km e tempo
- `Calculator.kt` - R\$/km, R\$/h, classificacao e nota
- `Validator.kt` - coerencia e valores suspeitos
- `OverlayManager.kt` - card flutuante Compose (arrastavel, minimizavel, lembra posicao)
- `OcrFallback.kt` - fallback ML Kit com MediaProjection
- `RideForegroundService.kt` - servico em primeiro plano
- `MainActivity.kt` + `ui/` - interface escura (verde #31F900, rosa #C864AF)
