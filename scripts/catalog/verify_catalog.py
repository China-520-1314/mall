"""Read-only verification of imported product APIs and all local product images."""
import hashlib
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
BASE = 'http://localhost:8085'


def read_json(path):
    with urlopen(BASE + path, timeout=15) as response:
        body = json.load(response)
    assert body['code'] == 200, body.get('message')
    return body['data']


def verify_product(pair):
    source, actual = pair
    detail = read_json('/product/detail/' + str(actual['id']))
    assert actual['name'] == source['name']
    assert float(actual['price']) == float(source['price_cny'])
    assert actual['productCategoryId'] == source['category_id']
    assert source['brief'] in detail['product']['detailDesc']
    assert '采集日期' in detail['product']['detailMobileHtml']
    assert source['source_url'] in detail['product']['detailMobileHtml']
    assert len(detail['skuStockList']) == 1
    sku = detail['skuStockList'][0]
    assert float(sku['price']) == float(source['price_cny']) and sku['stock'] > 0
    specs = json.loads(sku['spData'])
    assert specs == [{'key': '展示规格', 'value': source['sku_specification']}]
    assert detail['productAttributeValueList'], 'Product specification options missing'
    with urlopen(actual['pic'], timeout=15) as response:
        assert response.headers.get_content_type() == 'image/jpeg'
        assert hashlib.sha256(response.read()).hexdigest() == source['local_image_sha256']
    return {'id': actual['id'], 'name': actual['name'], 'category': source['category_name'], 'price': actual['price']}


def main():
    catalog = json.loads((ROOT / 'document/catalog/mi-20260911.json').read_text(encoding='utf-8'))
    listing = read_json('/product/search?pageNum=1&pageSize=200')
    by_sn = {p['productSn']: p for p in listing['list']}
    pairs = [(source, by_sn[source['product_sn']]) for source in catalog['products']]
    with ThreadPoolExecutor(max_workers=4) as pool:
        verified = list(pool.map(verify_product, pairs))
    recommended = []
    for page in (1, 2, 3):
        recommended.extend(read_json(f'/home/personalizedProductList?pageNum={page}&pageSize=40'))
    assert len({p['id'] for p in recommended}) == len(recommended), 'Repeated pagination items'
    assert {p['id'] for p in verified}.issubset({p['id'] for p in recommended})
    home = read_json('/home/content')
    assert any(p.get('productSn', '').startswith('WEB-MI-20260911-') for p in home['newProductList'])
    summary = {'total_visible': listing['total'], 'verified_new_products': len(verified),
               'verified_images': len(verified), 'recommendation_candidates': len(recommended), 'products': verified}
    out = ROOT / 'output/catalog-verification'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'result.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    sheet = Image.new('RGB', (8*160, 8*180), '#f5f6f8')
    draw = ImageDraw.Draw(sheet)
    for i, (source, actual) in enumerate(pairs):
        image = Image.open(ROOT / 'mall-portal/src/main/resources/static' / source['local_image_path'].lstrip('/'))
        image.thumbnail((144, 144))
        x, y = (i % 8)*160 + 8, (i // 8)*180 + 8
        sheet.paste(image, (x + (144-image.width)//2, y))
        draw.text((x, y+147), f'ID {actual["id"]} | CNY {actual["price"]}', fill='#333333')
    sheet.save(out / 'all-products.jpg', quality=90)
    print(json.dumps({k: v for k, v in summary.items() if k != 'products'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
