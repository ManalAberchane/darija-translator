<?php

declare(strict_types=1);

final class TranslationError extends RuntimeException
{
}

/**
 * Client for the Darija Translator REST API (POST /api/translate, HTTP Basic auth).
 */
final class DarijaTranslatorClient
{
    public function __construct(private readonly Config $config)
    {
    }

    /**
     * @return array{translation: string, sourceLanguage: ?string, targetLanguage: string, script: string, model: string}
     */
    public function translate(string $text, string $sourceLanguage = 'auto', string $script = 'arabic'): array
    {
        $payload = json_encode(
            ['text' => $text, 'sourceLanguage' => $sourceLanguage, 'script' => $script],
            JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR
        );
        $credentials = base64_encode($this->config->username . ':' . $this->config->password);

        $context = stream_context_create([
            'http' => [
                'method' => 'POST',
                'header' => implode("\r\n", [
                    'Content-Type: application/json',
                    'Accept: application/json',
                    'Authorization: Basic ' . $credentials,
                ]),
                'content' => $payload,
                'timeout' => 60,
                'ignore_errors' => true,
            ],
        ]);

        $body = @file_get_contents($this->config->apiUrl . '/api/translate', false, $context);
        if ($body === false) {
            throw new TranslationError("Cannot reach the translation API at {$this->config->apiUrl}. Is the backend running?");
        }

        $status = self::statusCode($http_response_header ?? []);
        $data = json_decode($body, true);
        if ($status !== 200) {
            $message = is_array($data) && isset($data['message']) ? $data['message'] : "HTTP $status";
            throw new TranslationError("The API answered $status: $message");
        }
        if (!is_array($data) || !isset($data['translation'])) {
            throw new TranslationError('The API returned an unexpected response');
        }
        return $data;
    }

    private static function statusCode(array $headers): int
    {
        foreach ($headers as $header) {
            if (preg_match('#^HTTP/\S+\s+(\d{3})#', $header, $match)) {
                $status = (int) $match[1];
            }
        }
        return $status ?? 0;
    }
}
