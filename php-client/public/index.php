<?php

declare(strict_types=1);

require __DIR__ . '/../src/Config.php';
require __DIR__ . '/../src/DarijaTranslatorClient.php';

session_start();

$languages = ['auto' => 'Detect language', 'English' => 'English', 'French' => 'French', 'Spanish' => 'Spanish',
    'German' => 'German', 'Italian' => 'Italian', 'Modern Standard Arabic' => 'Modern Standard Arabic'];

$text = '';
$sourceLanguage = 'auto';
$script = 'arabic';
$result = null;
$error = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['clear'])) {
        $_SESSION['history'] = [];
    } else {
        $text = trim((string) ($_POST['text'] ?? ''));
        $sourceLanguage = array_key_exists($_POST['sourceLanguage'] ?? '', $languages) ? $_POST['sourceLanguage'] : 'auto';
        $script = ($_POST['script'] ?? '') === 'latin' ? 'latin' : 'arabic';

        if ($text === '') {
            $error = 'Please enter some text to translate.';
        } else {
            try {
                $client = new DarijaTranslatorClient(Config::load(__DIR__ . '/../.env'));
                $result = $client->translate($text, $sourceLanguage, $script);
                $_SESSION['history'] = array_slice(
                    array_merge([['text' => $text, 'translation' => $result['translation'], 'script' => $result['script']]],
                        $_SESSION['history'] ?? []),
                    0,
                    10
                );
            } catch (TranslationError $e) {
                $error = $e->getMessage();
            }
        }
    }
}

$history = $_SESSION['history'] ?? [];
$h = static fn (?string $value): string => htmlspecialchars((string) $value, ENT_QUOTES, 'UTF-8');
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Darija Translator – PHP client</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
<main>
    <header>
        <h1>Darija Translator</h1>
        <p class="muted">PHP client for the Jakarta REST translation service</p>
    </header>

    <form method="post" class="card">
        <div class="row">
            <label>From
                <select name="sourceLanguage">
                    <?php foreach ($languages as $value => $label): ?>
                        <option value="<?= $h($value) ?>" <?= $value === $sourceLanguage ? 'selected' : '' ?>><?= $h($label) ?></option>
                    <?php endforeach; ?>
                </select>
            </label>
            <label>Script
                <select name="script">
                    <option value="arabic" <?= $script === 'arabic' ? 'selected' : '' ?>>Arabic letters (عربي)</option>
                    <option value="latin" <?= $script === 'latin' ? 'selected' : '' ?>>Latin letters (Arabizi)</option>
                </select>
            </label>
        </div>
        <textarea name="text" rows="5" maxlength="5000" placeholder="Text to translate into Moroccan Darija…" required><?= $h($text) ?></textarea>
        <button type="submit" class="primary">Translate to Darija</button>
    </form>

    <?php if ($error !== null): ?>
        <p class="card error" role="alert"><?= $h($error) ?></p>
    <?php endif; ?>

    <?php if ($result !== null): ?>
        <section class="card result">
            <p class="muted"><?= $h($result['sourceLanguage'] ?? 'Detected') ?> → <?= $h($result['targetLanguage']) ?> · <?= $h($result['model']) ?></p>
            <p class="translation" dir="<?= $result['script'] === 'latin' ? 'ltr' : 'rtl' ?>" id="translation"><?= $h($result['translation']) ?></p>
        </section>
    <?php endif; ?>

    <?php if ($history !== []): ?>
        <section class="card">
            <form method="post" class="row spread">
                <h2>Recent translations</h2>
                <button type="submit" name="clear" value="1" class="link">Clear</button>
            </form>
            <ul class="history">
                <?php foreach ($history as $item): ?>
                    <li>
                        <span class="muted"><?= $h($item['text']) ?></span>
                        <span dir="<?= $item['script'] === 'latin' ? 'ltr' : 'rtl' ?>"><?= $h($item['translation']) ?></span>
                    </li>
                <?php endforeach; ?>
            </ul>
        </section>
    <?php endif; ?>
</main>
</body>
</html>
