# The Last of Us Remastered · edição AYN Thor

Versão do port **The Last of Us Remastered — Daedalus Android v0.1** (erickmendes22, baseado no port de PC de ravencaio) ajustada para o **AYN Thor** (Snapdragon 8 Gen 2, Adreno 740, Android 13).

A parte nativa do APK (FEX, renderizador Vulkan, runtime Bionic, Python e ferramentas de PKG) é copiada **byte a byte** do APK oficial v0.1, conferido pelo SHA-256 publicado. Só o launcher em Java é recompilado. Assim o jogo roda exatamente o mesmo código que o autor testou, e as mudanças ficam no que o launcher entrega ao jogo.

## O que muda

| Mudança | Por quê |
|---|---|
| **Driver Turnip Thor embutido** e escolhido por padrão na Adreno 740 (o Turnip R1 original continua na lista) | O renderizador do port é o do shadPS4 (`applicationName "shadPS4"`, igual ao Uncharted), então o perfil de ports do Turnip Thor vale aqui: texturas BC e render targets tiled em vez de lineares, autotune `profiled`, concurrent binning, GPU e L3 no máximo durante as submissões. |
| **Cache de shaders do driver ligado** (`MESA_SHADER_CACHE_DIR` + `MESA_SHADER_CACHE_DISABLE=false`) | O port não salva o `VkPipelineCache` em disco, e o Mesa deixa o cache em disco desligado no Android. O jogo roda como executável separado do app, então nem o patch de cache do Turnip Thor acha a pasta sozinho. Sem isso, toda sessão recompila todos os pipelines: são as travadas ao entrar numa área. Funciona com qualquer Turnip, inclusive o R1. |
| **Threads principais fora dos núcleos pequenos** (`TLOU_FAST_RENDER_CORES=1`) | O runtime já tem essa política, mas o launcher original a desligou porque não ajudou no Snapdragon 8 Elite (que não tem núcleos pequenos). No 8 Gen 2 ela mantém as 4 threads críticas (main do jogo, processador de comandos da GPU, gravação de draws, gravador Vulkan) nos núcleos X3 + A715/A710 (3–7), longe dos 3 Cortex-A510. Os workers continuam com todos os núcleos. |
| **Limite de 30 FPS por padrão** no 8 Gen 2 (só em instalação nova) | Ver "Expectativa realista" abaixo. |
| **Correção da mira em 720p/540p/360p** | Commit `e89f357` do autor (posterior à v0.1): a tabela de patches normalizava a mira em pixels de 1080p em qualquer resolução. Só o arquivo `tlou_patches.py` dentro do `tools.zip` é trocado, do mesmo jeito que o próprio CI do autor faz. |
| **Cartão "AYN THOR"** em Ajustes | Botão de perfil recomendado (720p, 30 FPS, VSync, Turnip Thor, cache, núcleos grandes), chaves do cache e dos núcleos, limpar cache, e **variáveis de ambiente extras** (`NOME=VALOR`, uma por linha) para testes A/B do driver. Tudo aparece no log. |
| **Pacote próprio** (`com.daedalus.tlou.remastered.thor`, nome "TLOU Remastered · AYN Thor") | O APK original é assinado com uma chave que só o autor tem; com outro pacote a edição Thor instala **ao lado** do original, e dá para comparar os dois no mesmo lugar do jogo. |

## Expectativa realista

Os logs do próprio autor, no Snapdragon 8 Elite / Adreno 830 a 720p, dão cerca de **36 FPS**, com a thread do processador de comandos gastando **~21 ms por quadro** de CPU e, num perfil anterior, dois compute shaders de transformação de vértices custando **~14,5 ms** de GPU. O X3 do 8 Gen 2 tem uns 30–40% menos desempenho por núcleo que o Oryon do 8 Elite, e a Adreno 740 também fica atrás da 830. Então, no Thor, o mais provável é o jogo ficar **limitado pela CPU numa thread** em boa parte das cenas, na casa dos 20 e poucos FPS. Por isso esta edição foca em **não piorar essa thread** (núcleos grandes), **não travar** (cache de shaders) e **ritmo estável** (30 FPS). Baixar para 540p só ajuda quando o HUD mostrar a GPU perto de 100%.

## Como gerar o APK

1. No GitHub, aba **Actions** → **Build TLOU Thor** → **Run workflow** (deixe marcado "Embutir o driver Turnip Thor").
2. Espere terminar (uns 35–40 min: o driver compila primeiro).

O driver **não fica neste repositório**: o workflow compila o Turnip Thor a partir do repositório do driver ([turnip_aynthor_ports](https://github.com/josepiresrocha-source/turnip_aynthor_ports), branch `main`). Atualizou o driver lá, o próximo APK já vem com a versão nova. Os campos `driver_repo` e `driver_ref` do **Run workflow** permitem apontar para outra branch ou versão.
3. Baixe o artefato `TLOU-Remastered-AYN-Thor-...` e extraia o `.apk` do zip.

O workflow baixa o APK oficial v0.1 do release do autor, confere o SHA-256 (`ccbdbfee…f85`), monta a edição, assina e, no fim, confere que todo arquivo nativo é idêntico ao do APK oficial.

**Chave de assinatura.** A chave nunca fica no repositório. Na primeira execução o workflow cria uma e a guarda no cache do Actions deste repositório; as próximas usam a mesma, e o APK novo instala por cima do anterior. O GitHub apaga caches sem uso há 7 dias: se isso acontecer, o workflow avisa ("New signing key") e aí é preciso exportar os saves, desinstalar e instalar de novo. Para nunca depender do cache, dá para guardar a chave como *secret* do repositório (`TLOU_KEYSTORE_B64` com o arquivo PKCS12 em base64, alias `tlou-thor`, e `TLOU_KEYSTORE_PASSWORD`).

## Como instalar

1. No app original: **Ajustes → Saves → Exportar saves ZIP**.
2. Instale o APK da edição Thor (ela aparece como **TLOU Remastered · AYN Thor**, ao lado do original).
3. Na edição Thor: **Escolher pasta do jogo** e aponte para a **mesma pasta `CUSA00552`** que você já extraiu. Não precisa instalar o PKG de novo; o app só prepara o jogo para ele.
4. **Ajustes → Saves → Importar saves ZIP**.
5. **Ajustes → AYN THOR → Aplicar perfil recomendado do Thor**.

No Thor, use o modo de desempenho do sistema e a ventoinha no modo forte.

## Como testar (e o que me mandar)

- O HUD mostra GPU e CPU. **GPU perto de 100%** = limitado pela GPU (vale tentar 540p ou desligar efeitos na aba Patches). **GPU folgada com FPS baixo** = limitado pela CPU.
- Compare **Threads principais nos núcleos grandes** ligado e desligado, na mesma cena.
- A primeira sessão compila os shaders; o ganho do cache aparece **da segunda sessão em diante**.
- Teste do driver (precisa do Turnip Thor **v4** no repositório do driver): em **Variáveis de ambiente extras**, `TU_MUTABLE_UBWC=1` liga o UBWC experimental (o mesmo teste pendente do Uncharted). Se aparecer erro gráfico com o Turnip Thor, `TU_MUTABLE_TILING=0` desliga o tiling opcional de imagens de 4 bytes do perfil dos ports (o suspeito mais provável).
- Depois de jogar: **Sobre → Exportar logs** e me mande o ZIP. A linha `AYN Thor edition:` do log mostra o que estava ligado.

## Arquivos

- `android-launcher/` — launcher do commit `0066e7f` (o da v0.1) com as mudanças da edição Thor. O diff completo está em `patches/0001-launcher-AYN-Thor-edition-*.patch`.
- `android/tlou_patches.py` — do commit `bab5b59` do autor (correção da mira).
- `stage_apk.py` — copia a parte nativa do APK oficial, troca o `tlou_patches.py` no `tools.zip` e embute o driver.
- `check_apk.py` — confere o APK final contra o oficial.
- `.github/workflows/build-apk.yml` — o workflow **Build TLOU Thor**.

Código do port sob GPL-2.0 (`LICENSE`), como o original: https://github.com/erickmendes22/tlou-remastered-android
