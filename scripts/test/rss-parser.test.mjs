import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseRssXml } from '../../api-worker/rss-parser.mjs';

test('RSS parser handles attributes, CDATA and entity decoding', () => {
  const feed = parseRssXml('<rss><channel><item><title><![CDATA[Mika & friends]]></title><source url="https://publisher.test">News &amp; Co</source><guid isPermaLink="false">id</guid></item></channel></rss>');
  assert.equal(feed.rss.channel.item.length, 1);
  assert.equal(feed.rss.channel.item[0].source['#text'], 'News & Co');
  assert.equal(feed.rss.channel.item[0].title, 'Mika & friends');
});
test('RSS parser rejects malformed XML, oversized feeds and entity declarations', () => {
  for (const xml of ['<rss><channel></rss>', '<!DOCTYPE rss [<!ENTITY boom "bad">]><rss/>', 'x'.repeat(2_000_001)]) {
    assert.throws(() => parseRssXml(xml));
  }
});
