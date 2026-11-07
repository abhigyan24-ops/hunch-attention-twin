const assert = require('node:assert/strict');
const vscode = require('vscode');
exports.run = async () => {
 const extension = vscode.extensions.getExtension('dhyan.dhyan-vscode');
 assert.ok(extension, 'Extension must be discoverable');
 await extension.activate();
 assert.equal(extension.isActive, true);
 assert.ok((await vscode.commands.getCommands(true)).includes('dhyan.diagnostics'));
};
