# ModuleDLC

**The Open Source Platform for Script testing** — клиентский Fabric-мод для
создания и проверки собственных модулей Minecraft на JavaScript, Java и Kotlin.

## Возможности

- Загрузка скриптов и горячая перезагрузка при изменении файлов.
- Модули, настройки и HUD-элементы из Script API.
- Discord Rich Presence со ссылками на [сайт](https://reflectxx.github.io)
  и [документацию](https://reflectxx.github.io/docs).

> Скрипты выполняются внутри процесса Minecraft с его полномочиями. Запускайте
> только код, которому доверяете.

## Требования

- Minecraft Java Edition 1.21.11
- Java 21 или новее
- Fabric Loader 0.20.1 или новее
- Fabric API

## Сборка и запуск

Склонируйте репозиторий и выполните:

```bash
./gradlew build
```

Готовый мод будет создан в `build/libs/`. Для запуска клиента из среды
разработки используйте `./gradlew runClient`.

Для установки скопируйте собранный JAR в папку `mods` экземпляра Minecraft с
Fabric Loader и Fabric API.

## Документация

- [Быстрый старт](./docs/getting-started.md)
- [Script API](./docs/Script-API.md)
- [Примеры скриптов](./docs/examples.md)
- [Полный указатель документации](./docs/README.md)

Основная документация проекта написана на русском языке.

## Лицензия

Код проекта распространяется по лицензии [MIT](./LICENSE). Лицензии
сторонних ресурсов указаны рядом с ними: Lucide — в каталоге иконок, Huninn —
в `src/main/resources/assets/mre/fonts/OFL-Huninn.txt`, Nunito — в архиве
`src/client/resources/Nunito.zip` (`OFL.txt`).
