import * as vscode from 'vscode';
import { activate as activateSensor, deactivate } from './extension.ts';
export { deactivate };
export function activate(context: vscode.ExtensionContext) {
  return activateSensor(context, undefined, vscode);
}
