#!/bin/bash
# Рендер скриншотов магазина через headless Chrome. Запуск: bash render.sh
cd "$(dirname "$0")"; D=$(pwd); mkdir -p framed raw
CH="google-chrome --headless=new --no-sandbox --disable-gpu --hide-scrollbars --allow-file-access-from-files --virtual-time-budget=4000"
enc(){ python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$1"; }
shot(){ n=$1 m=$2 t=$3 s=$4
  $CH --window-size=1080,1920 --screenshot="$D/framed/$n.png" "file://$D/frame.html?m=$m&t=$(enc "$t")&s=$(enc "$s")" 2>/dev/null
  $CH --window-size=390,844 --force-device-scale-factor=2.769 --screenshot="$D/raw/$n.png" "file://$D/app-mock.html#$m" 2>/dev/null; }
shot 01-editor     editor      'Монтаж <em>в пару касаний</em>' 'Обрезка, склейка и порядок клипов на таймлайне'
shot 02-transitions transitions 'Плавные <em>переходы</em>'     'Наплыв, шторка, затемнение, вспышка, наезд'
shot 03-filters    filters     '12 <em>фильтров</em>'           'Кино, Драма, Винтаж, Ч/Б и другие'
shot 04-music      music       'Музыка <em>под настроение</em>' 'Громкость трека и оригинала, выбор старта'
shot 05-text       text        'Текст <em>поверх видео</em>'    'Размеры, стили и цвета'
shot 06-export     export      'Экспорт <em>MP4 720p</em>'      'Сохранение прямо в галерею'
shot 07-done       done        'Без <em>водяных знаков</em>'    'Без рекламы и регистрации — всё на устройстве'
shot 08-new        empty       'Начните <em>новый проект</em>'  'Добавьте видео из галереи'
