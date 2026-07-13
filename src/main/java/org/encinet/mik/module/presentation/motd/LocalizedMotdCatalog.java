package org.encinet.mik.module.presentation.motd;

import org.encinet.mik.module.i18n.Language;

final class LocalizedMotdCatalog {

    private LocalizedMotdCatalog() {}

    static MotdProfileSpec profile(Language language) {
        return switch (language) {
            case DE_DE -> localized(
                    "Kreativ<white> & Entspannt",
                    "Bauen · Entspannen · Musik · Spaß · AFK",
                    "Plasmo Voice wird für Sprachchat empfohlen",
                    "Brauchst du Hilfe? Besuche mcmik.top",
                    "Noch unsicher, ob du beitreten möchtest?",
                    "Hier ist es entspannt. Schau einfach vorbei.",
                    "Genug aktualisiert. Komm spielen!",
                    "Alle arbeiten gerade sehr konzentriert ... bestimmt.",
                    "Der Server ist im gemeinsamen AFK-Modus",
                    "Es ist schon spät. Denk an eine Pause.",
                    "Willkommen zurück, {player}",
                    "Schön, dich wiederzusehen, {player}. Mach es dir gemütlich."
            );
            case ES_ES -> localized(
                    "Creativo<white> y tranquilo",
                    "Construir · Relajarse · Música · Diversión · AFK",
                    "Recomendamos Plasmo Voice para el chat de voz",
                    "¿Necesitas ayuda? Visita mcmik.top",
                    "¿Aún estás pensando si entrar?",
                    "Este es un lugar tranquilo. Pasa y echa un vistazo.",
                    "Ya has actualizado bastante. ¡Entra a jugar!",
                    "Todo el mundo está trabajando muy duro... seguramente",
                    "El servidor está en modo AFK colectivo",
                    "Ya es tarde. Acuérdate de descansar.",
                    "¡Qué bueno verte de nuevo, {player}!",
                    "Bienvenido de nuevo, {player}. Ponte cómodo."
            );
            case FR_FR -> localized(
                    "Créatif<white> et détente",
                    "Construire · Se détendre · Musique · S'amuser · AFK",
                    "Plasmo Voice est recommandé pour le chat vocal",
                    "Besoin d'aide ? Consultez mcmik.top",
                    "Vous hésitez encore à nous rejoindre ?",
                    "L'ambiance est tranquille. Venez faire un tour.",
                    "Assez actualisé. Venez jouer !",
                    "Tout le monde travaille très sérieusement... sûrement",
                    "Le serveur est en mode AFK collectif",
                    "Il se fait tard. Pensez à vous reposer.",
                    "Bon retour parmi nous, {player} !",
                    "Content de vous revoir, {player}. Installez-vous."
            );
            case IT_IT -> localized(
                    "Creativa<white> e rilassata",
                    "Costruire · Rilassarsi · Musica · Divertimento · AFK",
                    "Plasmo Voice è consigliato per la chat vocale",
                    "Serve aiuto? Visita mcmik.top",
                    "Stai ancora pensando se entrare?",
                    "Qui l'atmosfera è tranquilla. Vieni a dare un'occhiata.",
                    "Hai aggiornato abbastanza. Entra a giocare!",
                    "Stanno tutti lavorando sodo... probabilmente",
                    "Il server è in modalità AFK collettiva",
                    "Si è fatto tardi. Ricordati di riposare.",
                    "Bentornato, {player}!",
                    "Che bello rivederti, {player}. Mettiti comodo."
            );
            case JA_JP -> localized(
                    "クリエイティブ<white>＆のんびり",
                    "建築 · のんびり · 音楽 · 楽しむ · AFK",
                    "ボイスチャットにはPlasmo Voiceがおすすめです",
                    "お困りですか？ mcmik.topをご覧ください",
                    "まだ参加しようか迷っていますか？",
                    "のんびりした場所です。気軽に遊びに来てください。",
                    "更新はそのくらいにして、遊びましょう！",
                    "みんな真面目に作業中です...たぶん",
                    "サーバー全体がAFKモードです",
                    "夜も遅いので、そろそろ休みましょう",
                    "おかえりなさい、{player}！",
                    "また会えましたね、{player}。ごゆっくりどうぞ。"
            );
            case KO_KR -> localized(
                    "크리에이티브<white> & 여유",
                    "건축 · 휴식 · 음악 · 즐거움 · AFK",
                    "음성 채팅에는 Plasmo Voice를 추천합니다",
                    "도움이 필요하신가요? mcmik.top을 확인하세요",
                    "아직 들어올지 고민 중이신가요?",
                    "편안한 곳이니 가볍게 둘러보세요.",
                    "새로 고침은 그만하고 함께 플레이해요!",
                    "모두 열심히 작업 중입니다... 아마도요",
                    "서버가 단체 AFK 모드입니다",
                    "시간이 늦었어요. 잠시 쉬어 가세요.",
                    "다시 오신 것을 환영합니다, {player}님!",
                    "또 만나서 반가워요, {player}님. 편하게 즐기세요."
            );
            case NL_NL -> localized(
                    "Creatief<white> en ontspannen",
                    "Bouwen · Ontspannen · Muziek · Plezier · AFK",
                    "Plasmo Voice wordt aanbevolen voor spraakchat",
                    "Hulp nodig? Ga naar mcmik.top",
                    "Twijfel je nog om mee te doen?",
                    "Het is hier rustig. Kom gerust even kijken.",
                    "Genoeg vernieuwd. Kom spelen!",
                    "Iedereen is heel hard aan het werk... vast wel",
                    "De server staat in gezamenlijke AFK-modus",
                    "Het wordt laat. Vergeet niet te rusten.",
                    "Welkom terug, {player}!",
                    "Goed je weer te zien, {player}. Maak het jezelf gemakkelijk."
            );
            case PT_BR -> localized(
                    "Criativo<white> e tranquilo",
                    "Construir · Relaxar · Música · Diversão · AFK",
                    "Recomendamos o Plasmo Voice para o chat de voz",
                    "Precisa de ajuda? Acesse mcmik.top",
                    "Ainda está pensando se vai entrar?",
                    "O clima aqui é tranquilo. Entre para conhecer.",
                    "Chega de atualizar. Entre para jogar!",
                    "Todo mundo está trabalhando muito... provavelmente",
                    "O servidor está em modo AFK coletivo",
                    "Está ficando tarde. Lembre-se de descansar.",
                    "Boas-vindas de volta, {player}!",
                    "Que bom ver você de novo, {player}. Fique à vontade."
            );
            case RU_RU -> localized(
                    "Творчество<white> и отдых",
                    "Строительство · Отдых · Музыка · Веселье · AFK",
                    "Для голосового чата рекомендуем Plasmo Voice",
                    "Нужна помощь? Загляните на mcmik.top",
                    "Всё ещё решаете, стоит ли зайти?",
                    "Здесь спокойно. Заходите осмотреться.",
                    "Хватит обновлять список. Пора играть!",
                    "Все очень заняты делом... наверное",
                    "На сервере общий режим AFK",
                    "Уже поздно. Не забудьте отдохнуть.",
                    "С возвращением, {player}!",
                    "Рады снова вас видеть, {player}. Располагайтесь."
            );
            case TH_TH -> localized(
                    "สร้างสรรค์<white>และสบาย ๆ",
                    "สร้าง · พักผ่อน · เพลง · สนุก · AFK",
                    "แนะนำ Plasmo Voice สำหรับแชทเสียง",
                    "ต้องการความช่วยเหลือ? ไปที่ mcmik.top",
                    "ยังตัดสินใจอยู่ว่าจะเข้ามาไหม?",
                    "ที่นี่เล่นกันสบาย ๆ ลองเข้ามาดูได้เลย",
                    "รีเฟรชพอแล้ว เข้ามาเล่นกันเถอะ!",
                    "ทุกคนกำลังตั้งใจทำงาน... น่าจะนะ",
                    "เซิร์ฟเวอร์อยู่ในโหมด AFK พร้อมกัน",
                    "ดึกแล้ว อย่าลืมพักผ่อนนะ",
                    "ยินดีต้อนรับกลับมา {player}!",
                    "ดีใจที่ได้เจอกันอีก {player} เล่นให้สนุกนะ"
            );
            case UK_UA -> localized(
                    "Творчість<white> і відпочинок",
                    "Будівництво · Відпочинок · Музика · Розваги · AFK",
                    "Для голосового чату радимо Plasmo Voice",
                    "Потрібна допомога? Завітайте на mcmik.top",
                    "Усе ще вирішуєте, чи приєднатися?",
                    "Тут спокійно. Заходьте подивитися.",
                    "Досить оновлювати список. Час грати!",
                    "Усі дуже зайняті справами... мабуть",
                    "На сервері спільний режим AFK",
                    "Уже пізно. Не забудьте відпочити.",
                    "З поверненням, {player}!",
                    "Раді знову вас бачити, {player}. Влаштовуйтеся зручніше."
            );
            default -> throw new IllegalArgumentException("No localized MOTD profile for " + language.id());
        };
    }

    private static MotdProfileSpec localized(
            String category,
            String normal,
            String voice,
            String help,
            String deciding,
            String friendly,
            String join,
            String afk,
            String afkMode,
            String night,
            String welcome,
            String welcomeAgain
    ) {
        return new MotdProfileSpec(
                "<gold>Mi<white>k",
                "<gold>" + category,
                new String[]{
                        "<gradient:#5e4fa2:#f79459>" + normal + "</gradient>",
                        "<white>" + voice + "</white>",
                        "<gradient:#ee9ca7:#ffdde1>" + help + "</gradient>",
                },
                new String[][]{{
                        "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                        "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                        "<gradient:#84fab0:#8fd3f4>" + deciding + "</gradient>",
                        "<white>" + friendly + "</white>",
                        "<bold><gradient:#42e695:#3bb2b8>" + join + "</gradient></bold>",
                }},
                new String[]{
                        "<gradient:#ffd89b:#19547b>" + afk + "</gradient>",
                        "<gray>" + afkMode + "</gray>",
                },
                new String[]{"<gradient:#7f7fd5:#86a8e7:#91eae4>" + night + "</gradient>"},
                new String[]{
                        "<white>" + welcome.replace("{player}", "</white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>"),
                        "<white>" + welcomeAgain.replace("{player}", "</white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>") + "</white>",
                }
        );
    }
}
