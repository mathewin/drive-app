# DriveWin

App Android nativo que le a oferta da Uber, 99, inDriver e Bolt via acessibilidade e mostra em tempo real, num card flutuante colorido, quanto a corrida paga por km e por hora em relacao as metas do motorista.

## Como preparar para o teste de rua

1. Abra o projeto no Android Studio (pasta `app` -> botao Run / Build APK).
2. Instale no celular e abra o DriveWin.
3. Na aba **Leitura**, toque em cada botao `Conceder` e confirme:
   - **Acessibilidade** -> ative o "DriveWin - Calculadora de ganhos"
   - **Sobreposicao** -> permitir exibir sobre outros apps
   - **Otimizacao de bateria** -> isentar (essencial no Xiaomi/MIUI, senao o servico e morto no meio do dia)
4. No Xiaomi/MIUI, libere tambem o **autostart** do DriveWin: app Seguranca -> Definicoes -> Apps -> Iniciar automaticamente -> ligar DriveWin.
5. O status deve ficar "AGUARDANDO CORRIDA".
6. Na aba **Calculo**, ajuste as metas de R\$/km e R\$/h, posicao, opacidade, fonte, tempo de exibicao e o alerta sonoro/vibracao.
7. Abra a Uber ou a 99 e aguarde uma oferta. O card aparece sozinho no topo (ou posicao escolhida), bipa e vibra.

## Depurando na rua

Com o celular no modo desenvolvedor via USB:

```
adb logcat -s DriveWin
```

A tag `DriveWin` mostra:
- `service connected` -> servico de acessibilidade ativo
- `overlay shown fare=... km=... min=... verdict=...` -> card exibido
- `oferta detectada mas sem card` -> viu a oferta mas nao achou o card
- `parse falhou textos=...` -> o card nao foi lido; mande esse texto para ajustar o parser

## Estrutura

- `CalculatorService.kt` - servico de acessibilidade
- `RideCardParser.kt` - le valor, km e tempo do card
- `OverlayManager.kt` - card flutuante com tema por app (99 = preto/amarelo, Uber = branco/preto)
- `CalculoFragment.kt` - metas e aparencia do card
- `RideHistory.kt` / `HistoricoFragment.kt` / `DesempenhoFragment.kt` - historico e medias do dia
