# Minimo Keyboard

Teclado Android personalizable, con foco en una interfaz compacta, temas visuales y funciones prácticas para escribir. Proyecto desarrollado en Kotlin para Android Studio.

> **Estado:** código fuente en desarrollo. El repositorio no incluye un APK precompilado y la compilación debe verificarse en el entorno local antes de distribuir una versión.

## Funciones destacadas

- Teclado alfabético QWERTY en español, con Ñ, además de modos numérico y de emojis.
- Temas visuales, incluido modo AMOLED y opciones de color.
- Vibración y sonido de teclas configurables.
- Gestos para insertar espacios, borrar, mover el cursor y acceder a frases rápidas.
- Frases personalizables y acceso a símbolos.
- MusicTicker: integración con sesiones multimedia y portada en la barra espaciadora, con indicador de reproducción.
- Efectos RGB y ajustes de tamaño.
- Interfaz de configuración multilingüe: español, portugués, inglés y japonés.
- Compatibilidad declarada desde Android 8.0 (API 26).

Las funciones disponibles pueden variar según la aplicación de destino, la versión de Android y los permisos habilitados por el usuario.

## Requisitos

- Android Studio actualizado.
- Android SDK Platform 35 instalado.
- JDK compatible con la configuración Gradle del proyecto (el proyecto declara compatibilidad de compilación Java/Kotlin 22).
- Gradle/Android Gradle Plugin descargados por Android Studio durante la sincronización.

## Abrir y ejecutar

1. Descargá o cloná este repositorio.
2. Abrí la carpeta raíz `minimo-keyboard` desde Android Studio.
3. Aceptá la sincronización de Gradle e instalá los componentes del SDK que solicite el IDE.
4. Ejecutá **Build → Make Project** y corregí cualquier incompatibilidad de JDK/SDK que indique tu instalación.
5. Ejecutá la aplicación en un dispositivo o emulador Android.
6. En Android, habilitá Minimo Keyboard desde **Ajustes → Sistema → Idiomas y entrada → Teclado en pantalla** (la ruta puede variar según el fabricante).
7. Seleccionalo como teclado activo cuando quieras usarlo.

El proyecto no incluye el wrapper de Gradle (`gradlew`). Android Studio puede abrir y sincronizar el proyecto con la instalación de Gradle que gestione el IDE; si querés compilar desde Terminal, configurá Gradle y el JDK compatibles primero.

## MusicTicker y privacidad

La integración multimedia utiliza un servicio de escucha de notificaciones para detectar información de reproducción. Para usar esa función, Android puede solicitar que habilites el acceso a notificaciones para Minimo MusicTicker. Concedelo solo si estás de acuerdo con ese acceso.

El manifiesto del proyecto no declara permiso de Internet. Aun así, revisá el código y las dependencias antes de publicar nuevas versiones o distribuir compilaciones. No ingreses credenciales ni claves privadas en el repositorio.

## Estructura

```text
minimo-keyboard/
├── app/
│   └── src/main/
│       ├── java/com/miguel/keyboard/
│       ├── res/
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
├── CHANGELOG.md
└── LICENSE
```

## Reportar problemas

Abrí un issue con la versión de Android, el modelo del dispositivo, los pasos para reproducir el problema y el error completo. No adjuntes datos personales, registros sensibles ni información privada.

## Licencia

Este proyecto se distribuye bajo la licencia MIT. Consultá el archivo [`LICENSE`](LICENSE).
