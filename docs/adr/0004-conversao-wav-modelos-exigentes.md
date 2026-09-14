# Conversão local para WAV em modelos que exigem o contêiner

O Meta Muse Voice Transcribe recusa M4A ("requires WAV audio") no `audio/transcriptions`, enquanto a tela promete MP3/M4A/OGG/AAC. Para a combinação M4A + Meta, o app converte o arquivo inteiro para WAV 16 kHz mono no aparelho e segue no Meta, reaproveitando o fatiamento existente (que já preserva header WAV) — em vez de bloquear a combinação ou trocar de modelo em silêncio.

## Considered Options

- **Bloquear a combinação (rejeitado)**: barra Meta para não-WAV antes do envio. Evita o 400, mas quebra o fluxo de um toque e empurra a conversão para a taquígrafa.
- **Troca silenciosa para Whisper (rejeitado)**: tenta no Meta e, no 400 de contêiner, refaz no Whisper sem avisar. Fluido, mas troca modelo — e qualidade dos Segmentos — sem consentimento.
- **Converter e seguir no Meta (escolhido)**: preserva a escolha da usuária. Custa tempo/bateria e infla o payload (M4A de ~5 MB vira WAV de dezenas de MB), absorvidos pelo fatiamento. Só para modelos que declaram exigir WAV; os demais seguem como estão.

## Consequences

O pipeline ganha etapa de transcodificação antes do base64/fatiamento, só na rota dos modelos exigentes (capacidade declarada no catálogo; Meta exibe "WAV (converte M4A automaticamente)"). O Meta vai em JSON simples, sem Segmentos: os Trechos ficam estimados e sem palavra ativa (ver ADR-0003) — quem precisa de sincronia fina usa a ação "Tentar com Whisper". Erro 400 de contêiner nunca sugere mexer em timestamps; diz "Formato recusado pelo modelo" com [Tentar com Whisper] e [Escolher outro modelo]. O upload sanitiza `;` no nome do arquivo no mesmo pacote.
