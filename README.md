# Vehicle Studio — NeoForge 1.21.1

Crea **tus propios vehículos dentro de Minecraft**, desde cero y sin programar: coches, camionetas, autobuses, motos,
karts, tractores... y también **helicópteros, avionetas, drones y coches cohete**. Móntalos con cubos, elige y
retoca las **ruedas de referencia**, coloca los **asientos**, el volante, las luces y el escape, añade **hélices y
propulsores** para volar o ir más rápido, píntalos y **condúcelos con WASD**.

## Características

El editor funciona como el de Creature Studio: la **vista 3D ocupa casi toda la pantalla** y a la derecha hay un
panel con **pestañas con iconos** (teclas 1 a 8). **H** oculta el panel, **F** encuadra el modelo y la rueda del
ratón acerca hacia donde está el cursor.

| Pestaña | Qué hay dentro |
|---|---|
| **Plantilla** | 14 vehículos para empezar: coche, deportivo, camioneta, autobús, moto, kart, tractor, buggy, carreta, **helicóptero**, **avioneta**, **dron**, **coche cohete** y uno vacío (chasis, 4 ruedas y un asiento). Pasa el ratón para verlos en 3D y haz clic para usarlos. |
| **Piezas** | Añadir piezas de cada tipo: **carrocería** (cubos), **rueda**, **asiento**, **volante/manillar**, **luz**, **escape**, **hélice** y **propulsor**. Duplicar, espejar (crea la pieza del otro lado), borrar, centrar, nombre y «unida a». Para las luces eliges cuándo se encienden (faro, trasera o siempre) y para el volante su forma (volante o manillar de moto). **Sólida**: si los jugadores, los mobs y el suelo chocan con la pieza (quítalo en las que se puedan atravesar, como una cortina). |
| **Forma** | Posición, tamaño, pivote y giro en X/Y/Z (16 píxeles = 1 bloque), también con el ratón. |
| **Ruedas** | **10 ruedas de referencia** (coche, deportiva, camión, todoterreno, moto, bicicleta, tractor, carreta, kart y monster truck) que se ponen con un clic sin mover la rueda de sitio. Después se edita todo: diámetro, ancho, si **gira al doblar**, color y dibujo de la goma (lisa, bloques, tacos, tractor), color, forma (disco, tapacubos, 5 radios, estrella, bicicleta, madera) y tamaño de la llanta. Botones **Igual en todas las ruedas** y **Poner 4 ruedas**. |
| **Asientos** | Hasta 12 asientos con 6 estilos (de coche, banco, sillín, de carreras, silla e invisible) y cuál es el del **conductor**. |
| **Pintar** | Color y más de 50 texturas (metal, fibra de carbono, chapa estriada, rejilla, cuadros, cuero, faro, cristal...), pintura píxel a píxel con pincel, goma y gotero, **minibloques** para los detalles y skins PNG (importar/exportar). |
| **Vuelo** | **Hélices** (mirando hacia delante como una avioneta o hacia arriba como un helicóptero; de 2 a 8 palas, diámetro y colores) y **propulsores** (turbina, cohete o tobera, con llama del color y el largo que quieras). **Vuela**: no, como un **helicóptero** (sube, baja y se queda quieto en el aire) o como un **avión** (necesita velocidad para despegar y para no caer), con su velocidad en el aire y lo rápido que sube. **Turbo** (tecla R): velocidad extra, cuánto dura y cuánto tarda en recargarse. |
| **Conducción** | Velocidad máxima (hasta 150 km/h), aceleración, frenos, marcha atrás, giro de las ruedas, derrape, cuánto sube (bloques), vida, escala, si **atropella** a los mobs, **maletero** (de 9 a 54 huecos), **bocina** (coche, camión, campana, bici, payaso, vaca) y sonido del motor (normal, eléctrico o ninguno). Muestra lo que cuesta en hierro. |

En la barra de la vista 3D, **Probar** hace rodar y girar las ruedas (y enciende las luces de freno) y **Luces**
enciende los faros. Con **Ratón** (tecla M) editas directamente sobre el modelo: mover (G), tamaño (T), pincel (B),
goma (E) y gotero (I). Deshacer/rehacer (Ctrl+Z / Ctrl+Y), guardar (Ctrl+S), duplicar (Ctrl+D).

**En el juego**
- Las ruedas giran al avanzar, las delanteras (las que «giran al doblar») apuntan hacia donde vas, el volante y el
  manillar se mueven, los faros y las luces de freno se encienden, sale humo del escape y suena el motor.
- Las **hélices** giran (despacio en marcha, rápido al acelerar o volar), los **propulsores** echan fuego (enorme con
  el turbo) y, al volar, el vehículo **se inclina**: un avión levanta el morro al subir y se ladea en las curvas; un
  helicóptero baja el morro al avanzar. En pantalla, la **altura** sobre el suelo y la barra del **turbo**.
- Los que vuelan no se caen a plomo: sin conductor, un helicóptero baja despacio hasta el suelo y un avión planea.
- Se conduce como en un juego de coches: las curvas dependen de la distancia entre ejes y, a mucha velocidad, se
  abren (frena o usa el freno de mano para girar cerrado). Velocímetro en pantalla mientras conduces.
- Los vehículos son **sólidos**, pieza por pieza y aunque estén girados en diagonal:
  - Nadie los atraviesa: los jugadores y los mobs chocan con sus paredes, se pueden subir encima y andar por su
    suelo, y entrar en uno hueco (la caja de una camioneta, un remolque) por donde está abierto.
  - El vehículo choca con el terreno con todas sus piezas: no se mete en las paredes ni en el suelo, apoya en lo que
    toque (un vehículo largo no se hunde en un agujero bajo el centro), sube escalones y no gira si al girar metería
    el morro o la cola en una pared. También choca con otros vehículos.
  - Las flechas y los objetos que caen chocan con él, y lo que se queda dentro cuando el vehículo se le echa encima
    sale empujado hacia fuera.
  - Se puede hacer clic en cualquier parte del vehículo, y una pieza tapa lo que tiene detrás.
- Los vehículos **andan siempre** (no gastan combustible).
- Funciona en un jugador y en servidores (el mod debe estar en el servidor y en los clientes).

## Controles

| Tecla | Qué hace |
|---|---|
| **V** | Abrir el taller de vehículos |
| **Clic derecho** en cualquier parte de un vehículo | Subirse (el primero que sube conduce) |
| **W / S** | Acelerar · frenar y marcha atrás |
| **A / D** | Girar |
| **Espacio** | Freno de mano (derrapa en las curvas). En los que vuelan: **subir** (y despegar) |
| **Ctrl** (correr) | En los que vuelan: **bajar** |
| **R** | **Turbo** (si tiene propulsores; se recarga al soltar la tecla) |
| **H** | Bocina |
| **G** | Encender/apagar las luces |
| **Mayús** | Bajarse |
| **Mayús + clic derecho** (desde fuera) | Abrir el maletero |
| **Clic izquierdo** (desde fuera) | Golpear el vehículo: al quedarse sin vida se rompe y suelta el vehículo y lo que había en el maletero (en creativo se rompe de un golpe, sin soltar nada) |

Todas las teclas se cambian en **Opciones → Controles** (apartado *Vehicle Studio* y los de movimiento de Minecraft).

## Cómo compilarlo con GitHub (sin instalar nada)

1. Crea un repositorio **nuevo** en GitHub (puede ser privado).
2. Descomprime el `.zip` y entra en la carpeta `VehicleStudio` (tiene menos de 100 archivos, se puede subir de una vez).
3. En la página del repositorio pulsa **Add file → Upload files** y arrastra **todo el contenido** de la
   carpeta (incluidas las carpetas `.github` y `gradle`, y los archivos `.gitignore` y `.gitattributes`).
   Pulsa **Commit changes**.
   - En Mac, Finder oculta las carpetas que empiezan por punto: pulsa `Cmd + Shift + .` para verlas.
   - Si la carpeta `.github` no se subiera, crea el workflow a mano: **Add file → Create new file**,
     escribe como nombre `.github/workflows/build.yml` y pega el contenido del archivo `build.yml`.
4. Ve a la pestaña **Actions**. La compilación empieza sola (o pulsa **Run workflow**). Tarda unos 5–10 minutos
   la primera vez porque descarga y prepara Minecraft.
5. Cuando aparezca el círculo verde ✔, entra en la ejecución y descarga el artefacto
   **VehicleStudio-NeoForge-1.21.1**. Dentro está el archivo `vehiclestudio-neoforge-1.21.1-1.2.0.jar`.

Para compilar en tu PC: instala Java 21 y ejecuta `gradlew build` (Windows) o `./gradlew build` (Linux/Mac).
El `.jar` queda en `build/libs/`.

## Instalación

1. Instala **NeoForge 21.1.x** para **Minecraft 1.21.1** (https://neoforged.net).
2. Copia el `.jar` en la carpeta `mods` de tu `.minecraft`.
3. En un servidor, ponlo también en su carpeta `mods`.

Vehicle Studio es un mod aparte: puede ir junto a Creature Studio en la misma carpeta `mods`.

## Cómo se usa

- **Abrir el taller**: pulsa **V** o haz clic derecho en el bloque **Taller de vehículos**. Receta (mesa de crafteo):

  ```
  Hierro  Hierro           Hierro
  Tablón  Mesa de crafteo  Tablón
  Tablón  Tablón           Tablón
  ```

- En la biblioteca: a la izquierda **Mis vehículos** con **+ Nuevo vehículo**, **Importar** y **Abrir carpeta**;
  a la derecha el vehículo elegido (girando, con las ruedas en marcha) con **Editar / Duplicar / Eliminar** y
  **Obtener vehículo / Colocar / Exportar**.
- **+ Nuevo vehículo** abre el editor en **Plantilla**. Para empezar desde cero elige **Vacío**.
- Medidas en píxeles: 16 = 1 bloque. El suelo está en Y = 0, el frente del vehículo mira hacia **-Z** (la flecha
  azul de la rejilla) y el **lado izquierdo es +X** (ahí va el conductor en las plantillas). **Centrar modelo** lo
  centra y lo apoya en el suelo.
- **Ruedas**: elige una rueda (clic en la vista 3D o con el selector) y pulsa una rueda de referencia; luego cambia lo
  que quieras. Marca **Gira al doblar** en las delanteras. La distancia entre las que giran y las que no decide lo
  cerradas que son las curvas.
- **Asientos**: cada asiento es un sitio para un jugador, mirando hacia delante. Marca uno como
  **Asiento del conductor** (si no, conduce el primero).
- **Guardar** lo sube al mundo. Después, en la biblioteca:
  - **Obtener vehículo**: te da el vehículo como objeto; úsalo sobre el suelo para colocarlo mirando hacia donde
    miras. En creativo es gratis; en **supervivencia cuesta lingotes de hierro**: 1 por cada bloque de carrocería
    (16×16×16 píxeles), 1 por rueda, 1 por asiento, 2 por hélice o propulsor y 4 más si vuela, de 1 a 64 (el coche
    de la plantilla cuesta 13).
  - **Colocar**: lo pone delante de ti (solo creativo u operadores).
  - **Exportar / Importar**: archivos `.json` en `.minecraft/vehiclestudio/` para compartirlos con amigos.
- Los vehículos de cada mundo se guardan en `<carpeta del mundo>/vehiclestudio/*.json`.
- En multijugador, solo el creador de un vehículo (o un operador) puede editarlo o borrarlo.
- Si borras un vehículo en la biblioteca, todas sus copias desaparecen del mundo (lo que hubiera en sus
  maleteros cae al suelo).

## Estructura del proyecto

```
src/main/java/com/vehiclestudio/
  VehicleStudio.java       Clase principal del mod
  data/                    Definición de vehículos, JSON, plantillas, ruedas, formas, física de conducción
  entity/                  La entidad vehículo: asientos, conducción, maletero, daño
  item/ block/ registry/   Objeto vehículo, bloque del taller y registros
  network/                 Paquetes cliente <-> servidor
  server/                  Guardado en el mundo, permisos y coste en hierro
  client/                  Renderizado 3D, editor, biblioteca, sonido del motor y velocímetro
src/main/resources/        Texturas, modelos, idiomas (es/en), receta y botín
.github/workflows/build.yml  Compilación automática en GitHub
```

## Problemas frecuentes

- **El workflow falla en «Compilar el mod»**: abre el paso para ver el error. Si es un problema de red
  al descargar dependencias, vuelve a ejecutarlo con **Re-run jobs**.
- **No puedo subirme**: el vehículo necesita al menos un asiento libre. Si ya hay alguien conduciendo, te sientas
  en otro asiento.
- **Me subo pero no se mueve**: solo conduce quien va en el asiento del conductor. Revisa en **Asientos** cuál es.
- **Gira demasiado poco o demasiado**: cambia **Giro de las ruedas** en **Conducción** o acerca/aleja las ruedas
  delanteras de las traseras. Sin ninguna rueda que «gire al doblar», el vehículo gira igual, como si las tuviera.
- **No sube los bloques**: sube **Sube bloques (altura)** en **Conducción** (1 = un bloque entero).
- **El avión no despega**: tiene que ir por el suelo al menos a la velocidad de despegue (la pestaña **Vuelo** la
  muestra) y pulsar **Espacio**. Si no llega, sube la velocidad máxima en **Conducción**.
- **No vuela**: en la pestaña **Vuelo** elige *Helicóptero* o *Avión* y añade al menos una hélice o un propulsor.
- **El turbo no hace nada**: necesita al menos un propulsor y un turbo mayor que 0 %. Cuando la barra se vacía,
  suelta **R** para que se recargue.
- **La llama apunta hacia otro lado**: sale por la cara de atrás (+Z) del propulsor; gíralo en **Forma** (por
  ejemplo 90° en X para que apunte hacia abajo).
- **Estoy de pie encima de un vehículo y se va sin mí**: Minecraft no lleva a las entidades que están encima de
  otra que se mueve (tampoco con las barcas); el vehículo te empuja. Para viajar, siéntate en un asiento.
- **Me paro un poco antes de una pared que va en diagonal**: Minecraft solo tiene cajas de choque rectas; cuando una
  pieza está girada, se cubre con cajas pequeñas que sobresalen un poquito (unos píxeles) de la pieza.
- **Atravieso una pieza**: mira en **Piezas** que tenga marcado **Sólida**. Los **minibloques** son decoración y no
  chocan.
- **Los faros no iluminan el suelo**: brillan (se ven aunque sea de noche), pero Minecraft no tiene luces que se
  muevan sin otros mods.
- **En supervivencia no me da el vehículo**: necesitas en el inventario los lingotes de hierro que indica la
  biblioteca.
- **No veo el vehículo**: comprueba que el mod está instalado también en el servidor.
- **Una textura sale morada y negra**: el identificador no existe; elige una de la lista o revisa el nombre
  (por ejemplo `minecraft:block/stone`).

Licencia MIT.
