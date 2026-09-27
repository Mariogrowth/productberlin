// Bootstrap tests render the real screen; serve its local decorative asset.
const path = require('path');
const arrow = path.resolve(config.basePath, '../../../../app-presentation/src/jsMain/resources/arrow.svg');
config.files.push({ pattern: arrow, included: false, served: true });
config.proxies['/arrow.svg'] = '/absolute' + arrow;
