<?php

declare(strict_types=1);

final class Config
{
    public function __construct(
        public readonly string $apiUrl,
        public readonly string $username,
        public readonly string $password,
    ) {
    }

    public static function load(string $envFile): self
    {
        $values = [];
        if (is_file($envFile)) {
            foreach (file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) as $line) {
                $line = trim($line);
                if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) {
                    continue;
                }
                [$key, $value] = array_map('trim', explode('=', $line, 2));
                $values[$key] = trim($value, "\"'");
            }
        }
        $get = static fn (string $key, string $default = ''): string =>
            (string) (getenv($key) !== false ? getenv($key) : ($values[$key] ?? $default));

        return new self(
            rtrim($get('API_URL', 'http://localhost:8080'), '/'),
            $get('API_USERNAME'),
            $get('API_PASSWORD'),
        );
    }
}
