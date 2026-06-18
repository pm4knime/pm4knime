import { URL, fileURLToPath } from "node:url";
import { resolve } from "path";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import monacoEditorPlugin, { type IMonacoEditorOpts } from "vite-plugin-monaco-editor";
import svgLoader from "vite-svg-loader";

// @ts-expect-error svgo.config is not typed
import { svgoConfig } from "@knime/styles/config/svgo.config";

const monacoEditorPluginDefault = (monacoEditorPlugin as any).default as (
  options: IMonacoEditorOpts,
) => any;

export default defineConfig({
  plugins: [
    vue(),
    monacoEditorPluginDefault({ languageWorkers: ["editorWorkerService"] }),
    svgLoader({ svgoConfig }),
  ],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  optimizeDeps: {
    exclude: ["@knime/scripting-editor", "@knime/components"],
    include: ["@knime/ui-extension-service"],
  },
  base: "./",
  build: {
    target: "esnext",
    rollupOptions: {
      input: {
        bpmn: resolve(__dirname, "src/views/bpmn/index.html"),
        jsgraphviz: resolve(__dirname, "src/views/jsgraphviz/index.html"),
        tracevariant: resolve(__dirname, "src/views/tracevariant/index.html"),
        stateAwareOcel: resolve(__dirname, "state-aware-ocel.html"),
      },
    },
  },
});
