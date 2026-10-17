# Minimo Keyboard

<table style="border-collapse: collapse; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.15); border-radius: 8px; overflow: hidden; font-family: Arial, sans-serif;">
  <caption style="caption-side: top; padding: 10px; font-size: 18px; font-weight: bold; background: #fff; border-bottom: 1px solid #e0e0e0;">
    Galería — haz clic para ver al 100%
  </caption>

  <tr>
    <td style="padding: 10px; text-align: center; border: 1px solid #e0e0e0;">
      <a href="https://github.com/user-attachments/assets/1d9444da-fd93-4a5d-b601-1cf9eb7091e6"
         target="_blank" rel="noopener noreferrer" style="display: inline-block;">
        <img src="https://github.com/user-attachments/assets/1d9444da-fd93-4a5d-b601-1cf9eb7091e6"
             alt="Multimedia" loading="lazy"
             style="width: 288px; height: auto; display: block; border-radius: 6px; cursor: pointer;">
      </a>
    </td>
    <td style="padding: 10px; text-align: center; border: 1px solid #e0e0e0;">
      <a href="https://github.com/user-attachments/assets/3007598e-5420-40a4-a6c4-9e4b6308079f"
         target="_blank" rel="noopener noreferrer" style="display: inline-block;">
        <img src="https://github.com/user-attachments/assets/3007598e-5420-40a4-a6c4-9e4b6308079f"
             alt="Multimedia (3)" loading="lazy"
             style="width: 288px; height: auto; display: block; border-radius: 6px; cursor: pointer;">
      </a>
    </td>
  </tr>

  <tr>
    <td style="padding: 10px; text-align: center; border: 1px solid #e0e0e0;">
      <a href="https://github.com/user-attachments/assets/93a5d894-ad40-4c8d-b8c9-1fa4c2226af1"
         target="_blank" rel="noopener noreferrer" style="display: inline-block;">
        <img src="https://github.com/user-attachments/assets/93a5d894-ad40-4c8d-b8c9-1fa4c2226af1"
             alt="Multimedia (2)" loading="lazy"
             style="width: 288px; height: auto; display: block; border-radius: 6px; cursor: pointer;">
      </a>
    </td>
    <td style="padding: 10px; text-align: center; border: 1px solid #e0e0e0;">
      <a href="https://github.com/user-attachments/assets/e5f646ce-4f91-4578-976c-d552054f1e62"
         target="_blank" rel="noopener noreferrer" style="display: inline-block;">
        <img src="https://github.com/user-attachments/assets/e5f646ce-4f91-4578-976c-d552054f1e62"
             alt="Multimedia (1)" loading="lazy"
             style="width: 288px; height: auto; display: block; border-radius: 6px; cursor: pointer;">
      </a>
    </td>
  </tr>
</table>

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
=======
Minimo Keyboard es un teclado Android de entrada de texto basado en InputMethodService. Está pensado para ofrecer una experiencia simple y altamente configurable, con funcionamiento local y sin depender de conexión a Internet. La versión 1.26 incorpora además navegación mediante DPAD y soporte para dispositivos Android TV y equipos sin pantalla táctil.
>>>>>>> 5997819e0a4d05f9f2f28119f0e8bcb49d8ea2b0
