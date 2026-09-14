package com.example.data.provider

/** Falha tipada para recusa de conteiner: permite a UI oferecer acao de um toque. */
class ContainerRefusedException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

object OpenRouterErrors {
    /**
     * Verdadeiro quando o corpo do erro indica recusa de conteiner (exige WAV/RIFF).
     * Nao confunde com erro de `verbose_json` nao suportado, que pede outro tratamento.
     */
    fun isContainerRefusal(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val b = body.lowercase()
        if ("riff/wave" in b) return true
        return "wav" in b && ("riff" in b || "wave" in b || "container" in b || "requires" in b)
    }

    /** Mensagem em vocabulario do dominio: fala em WAV e Whisper, nunca em timestamps. */
    fun containerRefusedMessage(modelLabel: String): String =
        "Formato recusado pelo modelo $modelLabel: ele exige áudio WAV. " +
            "Converta o arquivo para WAV ou toque em Tentar com Whisper para usar o Whisper com sincronia de Trechos."
}
