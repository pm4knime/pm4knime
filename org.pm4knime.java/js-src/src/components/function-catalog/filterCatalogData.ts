import type { SelectableItem } from "@/components/function-catalog/catalogTypes";
import type { CatalogData } from "@/components/function-catalog/mapFunctionCatalogData";
import type { FunctionCatalogEntryData } from "@/components/functionCatalogTypes";

const filterBySearchTerm = (
  datum: FunctionCatalogEntryData,
  searchTerm: string,
): boolean =>
  datum.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
  datum.keywords.some((keyword) =>
    keyword.toLowerCase().includes(searchTerm.toLowerCase()),
  ) ||
  datum.category.toLowerCase().includes(searchTerm.toLowerCase());

export const filterCatalogData = (
  searchTerm: string,
  categories: { [key: string]: { expanded: boolean } },
  catalogData: CatalogData,
): {
  filteredCatalogData: CatalogData;
  orderOfItems: SelectableItem[];
} => {
  const filteredCatalogData: CatalogData = { ...catalogData };
  const orderOfItems: SelectableItem[] = [];
  for (const category of Object.keys(categories)) {
    filteredCatalogData[category] = filteredCatalogData[category].filter(
      (entry) => filterBySearchTerm(entry, searchTerm),
    );

    if (filteredCatalogData[category].length !== 0) {
      orderOfItems.push({ type: "category", name: category });
      if (categories[category].expanded) {
        const functionsToPutIn: SelectableItem[] = filteredCatalogData[
          category
        ].map((functionData) => ({
          type: "function",
          functionData,
        }));
        orderOfItems.push(...functionsToPutIn);
      }
    }
  }

  return {
    filteredCatalogData,
    orderOfItems,
  };
};
