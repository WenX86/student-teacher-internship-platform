import { computed, ref, unref, watch } from "vue";

function containsKeyword(values, keyword) {
  return values.filter((value) => value !== undefined && value !== null && value !== "").some((value) => String(value).includes(keyword));
}

export function useFilteredPagination({ source, matcher, pageSize = 10, initialKeyword = "", initialPage = 1 }) {
  const keyword = ref(initialKeyword);
  const currentPage = ref(initialPage);

  const filteredItems = computed(() => {
    const items = unref(source) || [];
    const normalizedKeyword = keyword.value.trim();

    if (!normalizedKeyword) {
      return items;
    }

    return items.filter((item) => containsKeyword(matcher(item), normalizedKeyword));
  });

  const pagedItems = computed(() => {
    const start = (currentPage.value - 1) * pageSize;
    return filteredItems.value.slice(start, start + pageSize);
  });

  watch(keyword, () => {
    currentPage.value = initialPage;
  });

  watch(
    filteredItems,
    (items) => {
      const maxPage = Math.max(initialPage, Math.ceil(items.length / pageSize) || initialPage);
      if (currentPage.value > maxPage) {
        currentPage.value = maxPage;
      }
    },
    { immediate: true }
  );

  return {
    keyword,
    currentPage,
    filteredItems,
    pagedItems,
  };
}
