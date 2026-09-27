import { XMLParser, XMLValidator } from 'fast-xml-parser';

const parser = new XMLParser({
  ignoreAttributes: false,
  parseTagValue: false,
  trimValues: true,
  isArray: (_name, path) => path === 'rss.channel.item',
});

export function parseRssXml(xml) {
  if (xml.length > 2_000_000 || /<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('Unsupported RSS document');
  if (XMLValidator.validate(xml) !== true) throw new Error('Malformed RSS XML');
  return parser.parse(xml);
}
