# Shorts Auto Next

## PC — extensão (Chrome/Edge)
1. Descompacte a pasta `extensao`
2. Abra `chrome://extensions` (ou `edge://extensions`)
3. Ative **Modo do desenvolvedor**
4. **Carregar sem compactação** → selecione a pasta `extensao`
5. Abra youtube.com/shorts → botão vermelho no canto inferior esquerdo

Controles:
- Botão flutuante ou **Alt+Shift+S** → liga/desliga
- Ícone da extensão → atraso, pular após X s, parar após N vídeos / N minutos

## Android — app
Gerar o APK (escolha um):
- **GitHub (sem instalar nada):** suba a pasta `android` num repositório → aba *Actions* → workflow "Gerar APK" → baixe o artefato `ShortsAutoNext-apk`
- **Android Studio:** File → Open → pasta `android` → Build → Build APK(s)

Instalar e usar:
1. Instale o APK (permitir "fontes desconhecidas")
2. Abra o app → **Ativar acessibilidade** → Shorts Auto Next → ativar
   - Android 13+: se aparecer "configuração restrita", vá em Configurações → Apps → Shorts Auto Next → ⋮ → *Permitir configurações restritas*
3. Abra os Shorts no YouTube → botão vermelho flutuante (toque = liga/desliga)

Como detecta o fim do Short:
- Lê a barra de progresso do YouTube quando ela estiver acessível
- Se não conseguir, avança após o **Tempo padrão** (30 s, ajustável)
- Ao bater limite de vídeos/minutos: desliga e volta para a tela inicial
