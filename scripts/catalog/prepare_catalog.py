"""Extract a reproducible Chinese catalog from a saved, public mi.com/shop page.

Requires beautifulsoup4 and Pillow. No page JavaScript is executed.
"""
import argparse
import hashlib
import json
from collections import Counter
from datetime import date
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlparse

from bs4 import BeautifulSoup
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / 'document/catalog'
ASSET_DIR = ROOT / 'mall-portal/src/main/resources/static/catalog/mi-20260911'

# Reuse the mall's existing taxonomy. Keep phones, computers and appliances balanced.
GROUPS = [
    ('手机', None, None, 8, 19, '手机通讯'),
    ('笔记本 | 平板', '热门', 'Book', 4, 54, '笔记本'),
    ('笔记本 | 平板', '热门', 'Pad', 4, 53, '平板电脑'),
    ('家电', '电视影音', None, 4, 35, '电视'),
    ('家电', '冰箱', None, 4, 38, '冰箱'),
    ('家电', '空调', None, 4, 36, '空调'),
    ('家电', '洗衣机', None, 4, 37, '洗衣机'),
    ('智能穿戴', '耳机', None, 4, 32, '影音娱乐'),
    ('智能穿戴', '穿戴', None, 4, 34, '智能设备'),
    ('智能家居', '路由器', None, 4, 33, '数码配件'),
    ('生活电器', '风扇', None, 2, 41, '生活电器'),
    ('生活电器', '扫地机', None, 2, 41, '生活电器'),
    ('生活电器', '空净', None, 2, 41, '生活电器'),
    ('生活电器', '清洁', None, 2, 41, '生活电器'),
    ('厨房电器', '电饭煲', None, 3, 40, '厨房小电'),
    ('厨房电器', '微蒸烤', None, 3, 40, '厨房小电'),
    ('厨房电器', '净水器', None, 3, 39, '厨卫大电'),
    ('厨房电器', '烟灶', None, 3, 39, '厨卫大电'),
]


def prepare(snapshot: Path):
    raw = snapshot.read_text(encoding='utf-8')
    floors = json.JSONDecoder().raw_decode(raw.split('goodsFloorData:', 1)[1].lstrip())[0]
    catalog = []
    seen = set()
    for floor_name, tab_name, keyword, count, category_id, category_name in GROUPS:
        floor = next(f['body'] for f in floors if f['body'].get('floor_name') == floor_name)
        tab = next(t for t in floor['tab_content'] if t['tab_name'] == tab_name) if tab_name else floor
        candidates = [p for p in tab['product_list'] if not keyword or keyword in p['product_name']]
        selected = candidates[:count]
        if len(selected) != count:
            raise ValueError(f'Missing expected products: {floor_name}/{tab_name}')
        for item in selected:
            source_id = str(item['product_id'])
            if source_id in seen:
                raise ValueError(f'Duplicate product: {source_id}')
            seen.add(source_id)
            price = Decimal(item['product_price'])
            image_url = item['img_url']
            if price <= 0 or urlparse(image_url).hostname not in ('cdn.cnbj1.fds.api.mi-img.com', 'cdn.cnbj0.fds.api.mi-img.com'):
                raise ValueError('Unexpected price/image source')
            title = BeautifulSoup(item['product_name'], 'html.parser').get_text(' ', strip=True)
            brief = BeautifulSoup(item.get('product_brief', ''), 'html.parser').get_text(' ', strip=True)
            catalog.append({
                'source_id': source_id,
                'source_url': item['action']['path'],
                'name': title,
                'brief': brief,
                'brand_id': 6,
                'brand_name': '小米',
                'category_id': category_id,
                'category_name': category_name,
                'price_cny': str(price),
                'original_price_cny': str(max(price, Decimal(item.get('product_org_price') or price))),
                'is_starting_price': bool(item.get('show_price_qi')),
                'source_image_url': image_url,
                'local_image_path': f'/catalog/mi-20260911/{source_id}.jpg',
                'product_sn': f'WEB-MI-20260911-{source_id}',
                'sku_specification': '官网展示基础款' if item.get('show_price_qi') else '官网展示款',
                'demo_stock': 100,
                'source_section': f'{floor_name}/{tab_name or floor_name}',
            })
    document = {
        'source': '小米商城公开首页', 'source_url': 'https://www.mi.com/shop',
        'captured_on': date.today().isoformat(), 'currency': 'CNY',
        'snapshot_sha256': hashlib.sha256(snapshot.read_bytes()).hexdigest(),
        'notes': '名称、卖点、图片、价格来自公开页面；起售价不是具体容量/颜色报价。库存100为实训设定，销量初始为0，不导入虚构评价。',
        'products': catalog,
    }
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    target = DATA_DIR / 'mi-20260911.json'
    target.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'products': len(catalog), 'categories': dict(Counter(p['category_name'] for p in catalog)), 'manifest': str(target)}, ensure_ascii=False))


def finalize_images():
    document = json.loads((DATA_DIR / 'mi-20260911.json').read_text(encoding='utf-8'))
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    for p in document['products']:
        source = ROOT / 'output/catalog-images' / (p['source_id'] + '.source')
        with Image.open(source) as image:
            image.load()
            if min(image.size) < 150:
                raise ValueError(f'Image too small: {p["name"]}, {image.size}')
            image.thumbnail((640, 640))
            if image.mode == 'RGBA' or 'transparency' in image.info:
                rgba = image.convert('RGBA')
                canvas = Image.new('RGB', rgba.size, 'white')
                canvas.paste(rgba, mask=rgba.getchannel('A'))
                image = canvas
            else:
                image = image.convert('RGB')
            target = ASSET_DIR / (p['source_id'] + '.jpg')
            image.save(target, 'JPEG', quality=88, optimize=True)
            p['local_image_sha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
            p['image_size'] = list(image.size)
    (DATA_DIR / 'mi-20260911.json').write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Validated and optimized {len(document["products"])} product photos')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--snapshot', type=Path, default=ROOT / 'output/mi-shop.html')
    parser.add_argument('--images', action='store_true')
    args = parser.parse_args()
    finalize_images() if args.images else prepare(args.snapshot)
