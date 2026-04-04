export const getFirstImage = (images: string) => {
    if (!images) return '';
    try {
        const arr = JSON.parse(images);
        return Array.isArray(arr) && arr.length > 0 ? arr[0] : '';
    } catch {
        return '';
    }
};