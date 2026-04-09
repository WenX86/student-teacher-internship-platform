import { ElButton } from "element-plus/es/components/button/index";
import { ElCheckbox } from "element-plus/es/components/checkbox/index";
import { ElCol } from "element-plus/es/components/col/index";
import { ElContainer, ElAside, ElHeader, ElMain } from "element-plus/es/components/container/index";
import { ElConfigProvider } from "element-plus/es/components/config-provider/index";
import { ElDatePicker } from "element-plus/es/components/date-picker/index";
import { ElDescriptions, ElDescriptionsItem } from "element-plus/es/components/descriptions/index";
import { ElDialog } from "element-plus/es/components/dialog/index";
import { ElEmpty } from "element-plus/es/components/empty/index";
import { ElForm, ElFormItem } from "element-plus/es/components/form/index";
import { ElInput } from "element-plus/es/components/input/index";
import { ElInputNumber } from "element-plus/es/components/input-number/index";
import { ElLoading } from "element-plus/es/components/loading/index";
import { ElMenu, ElMenuItem } from "element-plus/es/components/menu/index";
import { ElPagination } from "element-plus/es/components/pagination/index";
import { ElProgress } from "element-plus/es/components/progress/index";
import { ElRow } from "element-plus/es/components/row/index";
import { ElSelect, ElOption } from "element-plus/es/components/select/index";
import { ElSpace } from "element-plus/es/components/space/index";
import { ElSwitch } from "element-plus/es/components/switch/index";
import { ElTable, ElTableColumn } from "element-plus/es/components/table/index";
import { ElTag } from "element-plus/es/components/tag/index";

import "element-plus/es/components/aside/style/css";
import "element-plus/es/components/button/style/css";
import "element-plus/es/components/checkbox/style/css";
import "element-plus/es/components/col/style/css";
import "element-plus/es/components/container/style/css";
import "element-plus/es/components/config-provider/style/css";
import "element-plus/es/components/date-picker/style/css";
import "element-plus/es/components/descriptions/style/css";
import "element-plus/es/components/descriptions-item/style/css";
import "element-plus/es/components/dialog/style/css";
import "element-plus/es/components/empty/style/css";
import "element-plus/es/components/form/style/css";
import "element-plus/es/components/form-item/style/css";
import "element-plus/es/components/header/style/css";
import "element-plus/es/components/input/style/css";
import "element-plus/es/components/input-number/style/css";
import "element-plus/es/components/loading/style/css";
import "element-plus/es/components/main/style/css";
import "element-plus/es/components/menu/style/css";
import "element-plus/es/components/menu-item/style/css";
import "element-plus/es/components/message/style/css";
import "element-plus/es/components/message-box/style/css";
import "element-plus/es/components/option/style/css";
import "element-plus/es/components/pagination/style/css";
import "element-plus/es/components/progress/style/css";
import "element-plus/es/components/row/style/css";
import "element-plus/es/components/select/style/css";
import "element-plus/es/components/space/style/css";
import "element-plus/es/components/switch/style/css";
import "element-plus/es/components/table/style/css";
import "element-plus/es/components/table-column/style/css";
import "element-plus/es/components/tag/style/css";

const components = [
  ElAside,
  ElButton,
  ElCheckbox,
  ElCol,
  ElContainer,
  ElConfigProvider,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElProgress,
  ElRow,
  ElSelect,
  ElSpace,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
];

export function setupElementPlus(app) {
  components.forEach((component) => {
    app.use(component);
  });

  app.use(ElLoading);
}
