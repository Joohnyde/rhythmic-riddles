/**
 * Backend Stage 1 album/category DTO.
 * Keep field names and nullability aligned with the backend JSON contract.
 */
export interface CategorySimple {
  id: string;
  name: string;
  image: string;
  /** Picker/team image reference used for the card marker, not a team id. */
  pickedByTeam: string | null;
  /** null means this category has not yet been picked. */
  ordinalNumber: number | null;
}

export interface AlbumCardVm {
  id: string;
  name: string;
  image: string;
  pickedByTeam: string | null;
  ordinalNumber: number | null;
}

export function toAlbumCardVm(album: CategorySimple): AlbumCardVm {
  return {
    id: album.id,
    name: album.name,
    image: album.image,
    pickedByTeam: album.pickedByTeam,
    ordinalNumber: album.ordinalNumber,
  };
}
