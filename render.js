var page = require('webpage').create(),
    system = require('system'),
    outname, fs = require("fs");
page.viewportSize = { width: 1920, height: 1080 };
if (system.args.length > 2) {
  outname=system.args[2];
} else {
  outname=system.args[1].replace('.html', '.png');
}
page.open(system.args[1], function () {
    page.render(outname);
    phantom.exit();
});
