import { Team } from './team.model';

export interface CategoryPreview {
  title: string;
  image: string;
}

/**
 * Backend selected album/category DTO.
 * Keep these field names aligned with backend JSON. Do not rename them unless the backend changes.
 */
export interface LastCategory {
  categoryId: string;
  chosenCategoryPreview: CategoryPreview;
  pickedByTeam: Team | null;
  started: boolean;
  ordinalNumber: number | null;
}
