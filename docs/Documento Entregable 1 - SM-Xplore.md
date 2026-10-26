# Documento Entregable 1 — SM-Xplore

---

## 1. PRESENTACIÓN

- **Programa:** Ingeniería de Sistemas
- **Periodo académico:** ________
- **Curso:** Arquitectura de Software
- **Código del curso:** ________
- **Créditos:** ________
- **Semestre:** ________
- **Estudiantes - Código y nombre:**
  - ________ — Nestor Ivan Saumeth Peña
  - ________ — Andres Manuel Rudas De Horta
  - ________ — Luis Javier Saumeth Anaya
  - ________ — Juan Diego Agamez Espinosa
  - ________ — Carlos Daniel Pinto Pulido

- **Institución:** Universidad del Magdalena – Facultad de Ingeniería
- **Docente:** Ing. Carlos Henriquez Miranda PhD

---

## 2. IDENTIFICACIÓN DEL PROYECTO

- **Título del proyecto:** SM-Xplore — Plataforma de gestión turística para Santa Marta, Magdalena
- **Duración del proyecto:** 12 semanas (semestre)
- **Trabajo:** ☐ Individual ☒ En equipo (n° estudiantes: **5**)

---

## 3. DESCRIPCIÓN DEL PROBLEMA DE INGENIERÍA

Santa Marta es una ciudad con un enorme potencial turístico (playas, Sierra Nevada, Parque Tayrona, Minca, Taganga, centro histórico), pero carece de una plataforma digital integrada que permita a turistas y residentes descubrir, planificar y reservar experiencias turísticas de manera eficiente. Los visitantes dependen actualmente de información dispersa en múltiples fuentes (redes sociales, páginas web no oficiales, recomendaciones verbales), lo que dificulta la planificación y genera experiencias inconsistentes.

Además, existen necesidades adicionales que el proyecto debe atender:

- **Monitorear niveles de congestión** y redistribuir turistas entre las zonas de la ciudad.
- **Gestionar reportes de seguridad en tiempo real** para proteger la integridad de los visitantes.
- **Proveer herramientas de gestión** a prestadores de servicios, guías turísticos y entidades gestoras de destinos.
- **Generar indicadores y reportes** que apoyen la toma de decisiones estratégicas.

SM-Xplore busca resolver esta problemática ofreciendo una plataforma centralizada, confiable y bilingüe (español/inglés) que conecte la oferta turística local con la demanda de turistas nacionales e internacionales, con énfasis en seguridad, planificación y gestión eficiente.

---

## 4. PROCESO DE DISEÑO DE INGENIERÍA

### 4.1. Definir Condiciones de Desarrollo (Breve Justificación)

#### 4.1.1. Proceso

Se emplea **RUP (Rational Unified Process)** con iteraciones de 15 días, dado que es un proceso iterativo e incremental que permite gestionar el riesgo desde las primeras fases y adaptarse a cambios en los requerimientos, propios de un proyecto académico multidisciplinario con cinco integrantes.

#### 4.1.2. Base de datos

Se adoptará un enfoque **multi-modelo** acorde a la naturaleza de los datos:

- **PostgreSQL:** como base de datos relacional principal, para persistir la información transaccional estructurada (usuarios, roles, atractivos, servicios, eventos, reservas, reseñas). Soporta integridad referencial y transacciones ACID, indispensables para el módulo de reservas y pagos.
- **MongoDB:** para almacenar datos semi-estructurados y de alta variabilidad (contenido multimedia de atractivos, catálogos, perfiles, históricos de reseñas y métricas), aprovechando su esquema flexible.
- **Neo4J:** para modelar y consultar el **grafo de relaciones** del dominio turístico (recomendaciones basadas en cercanía, rutas/itinerarios entre atractivos, y relaciones de los usuarios con sitios, guías y servicios).

#### 4.1.3. Lenguaje

- **Java** para el desarrollo del backend, por su madurez, robustez, seguridad y amplia disponibilidad de bibliotecas para el ecosistema empresarial y la integración con servicios externos.
- **JavaScript / TypeScript** para el frontend, permitiendo un desarrollo ágil e interactivo de la interfaz web responsive.

#### 4.1.4. Framework

- **Spring Boot** para el backend: ofrece inversión de control, seguridad (Spring Security), acceso a datos reactivo (Spring WebFlux + Spring R2DBC sobre PostgreSQL) e integración con la pasarela de pagos PSE, OpenStreetMap y OpenMeteo.
- **React** para el frontend: permite construir una interfaz web responsive, moderna y reactiva, con soporte de internacionalización (español/inglés) y consumo de la API REST.

---

### 4.2. Definición de requerimientos del diseño

#### 4.2.1. Contexto del problema

SM-Xplore será una **aplicación web responsive** enfocada en la ciudad de Santa Marta (Magdalena) que permitirá a turistas, guías turísticos, prestadores de servicios turísticos, entidades gestoras de destinos y administradores descubrir, planificar, gestionar y disfrutar de experiencias turísticas. El sistema integrará información sobre atractivos, servicios, guías, rutas, eventos, reservas, reseñas y reportes de seguridad, y estará disponible en español e inglés.

**Integraciones externas definidas:**

- **OpenStreetMap (OSM):** georreferenciación y visualización de puntos de interés (POI) en el mapa.
- **OpenMeteo API:** información meteorológica actual y pronósticos para Santa Marta.
- **Pasarela PSE:** transacciones seguras en pesos colombianos.
- **Servicio de correo electrónico y notificaciones push** del navegador (Web Push API).

**Funciones principales del sistema:**

- Registro y autenticación de usuarios con roles diferenciados (Turista, Guía, Prestador de Servicios, Entidad Gestora, Administrador).
- Catálogo de atractivos turísticos con información multimedia, ubicación, horarios y capacidad.
- Gestión de servicios, actividades y eventos turísticos con disponibilidad, cupos y afluencia estimada.
- Sistema de reservas de servicios, actividades y entradas para eventos.
- Sistema de reseñas y calificaciones con moderación.
- Gestión de reportes de seguridad y alertas en tiempo real.
- Recomendaciones personalizadas basadas en ubicación y preferencias.
- Monitoreo de niveles de congestión por zonas y atractivos.
- Notificaciones automáticas (correo, push web).
- Indicadores, métricas y reportes para todos los roles.
- Administración de anomalías del sistema.
- Gestión de zonas turísticas de Santa Marta.
- Soporte bilingüe (español/inglés).

#### 4.2.2. Identificación de stakeholders

| Stakeholder | Interés / Necesidad |
| --- | --- |
| Turistas nacionales e internacionales | Descubrir y planificar experiencias turísticas de manera fácil, segura y confiable. |
| Guías turísticos certificados | Promocionar sus servicios, gestionar su agenda y mejorar su visibilidad. |
| Prestadores de servicios turísticos | Aumentar visibilidad, ventas y gestión eficiente de reservas (hoteles, restaurantes, operadores, etc.). |
| Entidades gestoras de destinos | Gestionar atractivos, eventos, zonas y monitorear la afluencia turística (ej. alcaldía, entes turísticos). |
| Administradores del sistema | Gestionar y moderar la plataforma, monitorear anomalías y seguridad. |
| Ministerio de Comercio, Industria y Turismo | Regular y promover la actividad turística. |
| Ministerio de Ambiente y Desarrollo Sostenible | Velar por la sostenibilidad ambiental de los destinos. |
| Autoridades de protección de datos (MinTic, DNDA) | Garantizar el cumplimiento normativo (Ley 1581 de 2012). |
| Plataformas de información externa | Intercambio de información (páginas web, redes sociales). |
| Universidad del Magdalena | Validar el proyecto académico con impacto social. |
| Alcaldía de Santa Marta / entes turísticos | Promover el turismo de la ciudad y tomar decisiones basadas en datos. |

#### 4.2.3. Diagramas de casos de uso

**Nivel 0 (alto nivel).** Se identifican 5 actores y 11 casos de uso principales, contenidos en `docs/modelos/uc-lvl-0/diagram.puml`:

| Actor | Casos de uso |
| --- | --- |
| Turista (T) | UC1, UC3, UC4, UC5, UC6, UC7, UC8, UC11 |
| Prestadora de Servicios (P) | UC3, UC7, UC8, UC9, UC11 |
| Guía Turístico (G) | UC1, UC4, UC5, UC7, UC8, UC11 |
| Entidad Gestora de Destinos (GD) | UC2, UC4, UC6, UC8, UC9, UC11 |
| Administrador (A) | UC1, UC9, UC10, UC11 |

Casos de uso: **UC1** Gestionar Usuarios · **UC2** Gestionar Destino Turístico · **UC3** Gestionar Servicios · **UC4** Gestionar Atractivos Turísticos · **UC5** Gestionar Actividades Turísticas · **UC6** Gestionar Eventos Turísticos · **UC7** Gestionar Reservas · **UC8** Gestionar Reseñas · **UC9** Visualizar indicadores y reportes · **UC10** Administrar Anomalías · **UC11** Gestionar Reportes de Seguridad.

**Nivel 1 (por actor).** Se han elaborado diagramas por actor para cada caso de uso en `docs/modelos/uc-lvl-1/` (ver `docs/modelos/uc-lvl-1/README.md` para el índice completo):

- **Gestionar Usuarios:** `gestionar-usuarios/{admin,guia-turistico,turista}-diagrama.puml`
- **Gestionar Destino Turístico:** `gestionar-destino/gestoras-diagrama.puml`
- **Gestionar Servicios:** `gestionar-servicios/{prestadora,turista}-diagrama.puml`
- **Gestionar Atractivos Turísticos:** `gestionar-atractivos/{gestoras,guia-turistico,turista}-diagrama.puml`
- **Gestionar Actividades Turísticas:** `gestionar-actividades/{guia-turistico,turista}-diagrama.puml`
- **Gestionar Eventos Turísticos:** `gestionar-eventos/{gestoras,turista}-diagrama.puml`
- **Gestionar Reservas:** `gestionar-reservas/{turista,prestadora,guia-turistico}-diagrama.puml`
- **Gestionar Reseñas:** `gestionar-reseñas/{prestadora,guia-turistico,gestoras,turista}-diagrama.puml`
- **Visualizar Indicadores y Reportes:** `visualizar-indicadores-y-reportes/{prestadora,gestoras,admin}-diagrama.puml`
- **Administrar Anomalías:** `administrar-anomalias/admin-diagrama.puml`
- **Gestionar Reportes de Seguridad:** `gestionar-reportes-de-seguridad/{gestoras,prestadora,guia-turistico,admin,turista}-diagrama.puml`

**Modelo de dominio:** el diagrama de clases se encuentra en `docs/modelos/domain/diagram.puml`. Utiliza herencia donde `Usuario` es la clase base y de ella derivan `Turista`, `Guia`, `PrestadoraServicios`, `GestoraDestinos` y `Admin`. Las entidades principales del dominio son `Zona`, `Sitio`, `Evento`, `Actividad`, `Servicio`, `Reserva`, `Review`, `Anomalía` y `ReporteSeguridad`.

#### 4.2.4. Especificación de requerimientos (ISO/IEC/IEEE 29148:2018)

La especificación completa de requisitos de software (SRS) se encuentra en `docs/Requirements/SM-Xplore_SRS.docx` (Versión 2.0 - FINAL, según ISO/IEC/IEEE 29148:2018). Se resume a continuación.

##### Requisitos funcionales (por módulo)

| Módulo | Requisitos | HU asociadas |
| --- | --- | --- |
| 1. Gestión de Usuarios | RF01–RF13 | US16 (Turista), US1–US8 (Admin) |
| 2. Gestión de Atractivos Turísticos | RF14–RF29 | US1–US6, US15 (Entidad), US1, US2, US4, US5, US6, US10, US11, US12 (Turista/Guía) |
| 3. Gestión de Servicios Turísticos | RF30–RF40 | US1–US5, US10, US14, US15, US17 (Prestador), US11 (Turista) |
| 4. Gestión de Actividades Turísticas | RF41–RF47 | US1–US3 (Guía), US13 (Turista) |
| 5. Gestión de Eventos Turísticos | RF48–RF54 | US7, US8, US9 (Entidad), US8, US13, US14 (Turista) |
| 6. Gestión de Reservas | RF55–RF68 | US9, US15 (Turista), US4, US5 (Guía), US6–US9, US16 (Prestador) |
| 7. Gestión de Reseñas | RF69–RF77 | US3 (Turista), US11 (Guía), US11, US18 (Prestador), US12 (Entidad), US15 (Admin) |
| 8. Gestión de Reportes de Seguridad | RF78–RF87 | US7 (Turista), US15, US16 (Guía), US17, US18 (Admin), US20 (Entidad) |
| 9. Gestión de Zonas Turísticas | RF88–RF90 | US13, US14, US16 (Entidad) |
| 10. Indicadores y Reportes | RF91–RF98 | US9, US10, US16 (Admin), US10, US11 (Entidad), US12, US13 (Prestador), US8 (Guía) |
| 11. Administración de Anomalías | RF99–RF102 | US11–US14 (Admin) |
| 12. Notificaciones y Recomendaciones | RF103–RF105 | US1, US10 (Turista/Guía), Varios |
| 13. Información Meteorológica | RF106 | — |
| 14. Internacionalización | RF107 | — |
| 15. Compartir en Redes Sociales | RF108 | — |

##### Requisitos no funcionales (RNF)

| ID | Categoría | Requisito |
| --- | --- | --- |
| RNF01 | Seguridad | Autenticación multifactor para administradores y operadores. |
| RNF02 | Seguridad | Cifrado de datos sensibles (bcrypt, TLS 1.3). |
| RNF03 | Escalabilidad | Atender al menos 1,000 usuarios concurrentes. |
| RNF04 | Disponibilidad | 99% de disponibilidad mensual en horario 24/7. |
| RNF05 | Usabilidad | Interfaz accesible conforme a WCAG 2.1 nivel AA. |
| RNF06 | Rendimiento | Carga de páginas menor a 3 segundos en conexiones 4G. |
| RNF07 | Mantenibilidad | Código modular con documentación técnica actualizada. |
| RNF08 | Portabilidad | Compatible con Chrome, Firefox, Safari y Edge, escritorio y móvil. |
| RNF09 | Privacidad | Cumplimiento de Ley 1581 de 2012 (Habeas Data Colombia). |
| RNF10 | Internacionalización | Soporte bilingüe español/inglés con archivos de traducción separados. |
| RNF11 | Confiabilidad | Tasa de error menor al 0.1% en transacciones de pago. |
| RNF12 | Seguridad | Validación de datos obligatorios en formularios críticos. |

##### Requisitos de interfaz

- **Interfaz de usuario:** aplicación web responsive adaptable a escritorio, tablet y móvil.
- **Interfaz externa:** pasarela PSE (pagos en pesos colombianos), OpenStreetMap (mapas y georreferenciación), OpenMeteo API (clima), servicio de correo electrónico y notificaciones push del navegador (Web Push API).

##### Matriz de trazabilidad

La SRS establece la trazabilidad completa entre los 108 Requisitos Funcionales (RF), los Casos de Uso (UC) y 108 Pruebas de Aceptación (PA). Ver `docs/Requirements/SM-Xplore_SRS.docx`, sección 5.

#### 4.2.5. Plan, cronograma y presupuesto de desarrollo

##### 4.2.5.1. Roles del equipo y su asignación

| Rol | Integrante | Participación |
| --- | --- | --- |
| Arquitecto de Software | Nestor Ivan Saumeth Peña | 60% |
| Desarrollador Back-end | Andres Manuel Rudas De Horta | 90% |
| Desarrollador Back-end | Luis Javier Saumeth Anaya | 90% |
| Desarrollador Front-end | Juan Diego Agamez Espinosa | 90% |
| Auxiliar | Carlos Daniel Pinto Pulido | 100% |

##### 4.2.5.2. Mecanismos de comunicación y coordinación

- Reuniones semanales de seguimiento (sprint planning y review).
- Herramientas colaborativas: repositorio Git (GitHub) y comunicación por WhatsApp.
- Documentación compartida en el repositorio del proyecto.
- Revisiones de código (pull requests) obligatorias antes de integrar cambios.

##### 4.2.5.3. Plan con costos y tiempo

**Metodología:** RUP con iteraciones de 15 días.

| Fase | Duración | Iteraciones |
| --- | --- | --- |
| Inicio | 2 semanas | 1 iteración |
| Elaboración | 4 semanas | 4 iteraciones |
| Construcción | 4 semanas | 4 iteraciones |
| Transición | 2 semanas | 2 iteraciones |
| **Total** | **12 semanas** | **12 iteraciones** |

**Presupuesto:**

| Concepto | Costo |
| --- | --- |
| Arquitecto de Software | $4.125 |
| Desarrolladores Back-end | $5.940 |
| Desarrollador Front-end | $1.125 |
| Analista | $0 |
| Auxiliares | $2.200 |
| **Costos totales** | **$14.729** |
| **Valor final del proyecto** | **$19.884** |

#### 4.2.6. Atributos de calidad (mínimo 5)

| # | Atributo | Justificación |
| --- | --- | --- |
| 1 | Seguridad | Protección de datos personales y transacciones financieras mediante PSE. Cumplimiento de la Ley 1581 de 2012. Validación de datos en formularios críticos. |
| 2 | Usabilidad | Interfaz intuitiva, accesible (WCAG 2.1 AA) y bilingüe para turistas nacionales e internacionales. |
| 3 | Rendimiento | Tiempos de respuesta menores a 3 segundos para garantizar buena experiencia en conexiones móviles. |
| 4 | Disponibilidad | Servicio 24/7 con 99% de uptime, crítico para turistas en diferentes zonas horarias, con monitoreo en tiempo real. |
| 5 | Escalabilidad | Capacidad de crecer con la demanda turística de Santa Marta (mínimo 1,000 usuarios concurrentes). |
| 6 | Mantenibilidad | Código modular y documentado para facilitar evoluciones futuras. |
| 7 | Portabilidad | Web responsive para funcionar en cualquier dispositivo con navegador moderno. |
| 8 | Internacionalización | Soporte bilingüe español/inglés para turistas internacionales. |
| 9 | Confiabilidad | Tasa de error menor al 0.1% en transacciones de pago. Sistema de detección y gestión de anomalías. |

#### 4.2.7. Análisis de restricciones y riesgos

| Tipo | Restricción / Riesgo | Impacto | Mitigación |
| --- | --- | --- | --- |
| Técnica | Dependencia de APIs externas (OSM, OpenMeteo, PSE) | Caídas de servicio afectan funcionalidad | Implementar caché, fallbacks y monitoreo |
| Económica | Presupuesto limitado | Limitación de recursos | Uso de tecnologías open source |
| Social | Adopción por parte de operadores turísticos locales | Requiere capacitación | Plan de onboarding y soporte |
| Normativa | Cumplimiento Ley 1581 de 2012 | Obligatorio | Políticas de privacidad claras |
| Ética | Veracidad de la información turística | Reputación del sistema | Moderación de contenido y reseñas |
| Técnica | Disponibilidad de guías y prestadores certificados | Oferta limitada | Alianzas con entidades locales |
| Seguridad | Reportes de seguridad falsos o maliciosos | Riesgo para usuarios | Sistema de moderación y validación |
| Técnica | Picos de demanda en temporadas altas | Degradación del servicio | Escalabilidad automática y balanceo de carga |

---

## 5. EVALUACIÓN (Para su conocimiento, NO se desarrolla)

### Criterios de Evaluación Asociados al SO2 (ABET)

| Indicador de Desempeño | Evidencia en la Actividad | Puntos |
| --- | --- | --- |
| Establece las necesidades específicas y las restricciones del problema. | Requisitos, restricciones, modelos y atributos de calidad definidos y justificados. | 60 |

**Rúbrica:** (ver imagen adjunta en `docs/Documento Entregable 1 (6).md`).

La entrega del documento y sus anexos estará acompañada de una sustentación, en la cual se evaluará tanto el trabajo grupal como el desempeño individual, considerando el dominio técnico del tema, la claridad en la comunicación y la capacidad de argumentación.

---

## Anexos

### Glosario

- **POI:** Point of Interest — Punto de Interés turístico.
- **API:** Application Programming Interface — Interfaz de Programación de Aplicaciones.
- **PSE:** Pagos Seguros en Línea — Pasarela de pagos colombiana.
- **OSM:** OpenStreetMap — Servicio de mapas colaborativo.
- **WCAG:** Web Content Accessibility Guidelines — Directrices de Accesibilidad para Contenido Web.
- **RUP:** Rational Unified Process — Proceso Unificado Racional.
- **SRS:** Software Requirements Specification — Especificación de Requisitos de Software.
- **UC:** Caso de Uso (Use Case).
- **HU:** Historia de Usuario.
- **RF / RNF:** Requisito Funcional / Requisito No Funcional.

### Referencias

- ISO/IEC/IEEE 29148:2018 — Systems and software engineering — Life cycle processes — Requirements engineering.
- IEEE 830:1998 — Recommended Practice for Software Requirements Specifications.
- Ley 1581 de 2012 — Habeas Data Colombia (protección de datos personales).
- Documentación oficial de OpenStreetMap: https://www.openstreetmap.org/
- Documentación oficial de OpenMeteo: https://open-meteo.com/
- Documentación oficial de PSE: https://www.pse.com.co/

### Fuentes del repositorio

- SRS completo: `docs/Requirements/SM-Xplore_SRS.docx`
- Historias de usuario: `docs/user-stories/{turista,guia-turistico,prestadoras-de-servicios,gestoras-destinos,admin}/us.md`
- Modelo de dominio: `docs/modelos/domain/diagram.puml`
- Casos de uso nivel 0: `docs/modelos/uc-lvl-0/diagram.puml`
- Casos de uso nivel 1: `docs/modelos/uc-lvl-1/`
- Stakeholders: `docs/stakeholders/sh.md`
- Plan RUP y roles: `docs/plan/Plan_RUP_SM-Xplore.xlsx` y `docs/plan/team.md`