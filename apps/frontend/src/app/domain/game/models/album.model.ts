/**
 * Backend album/category DTO.
 * Keep these field names aligned with backend JSON. Do not rename them unless the backend changes.
 */
export interface CategorySimple {
  id: string;
  name: string;
  image: string;
  pickedByTeam: string | null;
  ordinalNumber: number | null;
}

export interface AlbumCardVm {
  id: string;
  name: string;
  image: string;
  pickedByTeam: string | null;
  ordinalNumber: number | null;
  disabled: boolean;
  pickedByAdmin: boolean;
}

export function toAlbumCardVm(album: CategorySimple): AlbumCardVm {
  const disabled = album.ordinalNumber == null;

  return {
    id: album.id,
    name: album.name,
    image: album.image,
    pickedByTeam: album.pickedByTeam,
    ordinalNumber: album.ordinalNumber,
    disabled,
    pickedByAdmin: disabled && album.pickedByTeam == null,
  };
}
